package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.dto.AcademicDtos.PlaceStudentRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.StudentPlacementResponse;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.student.Student;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Places students into sections with a roll number for an academic year.
 * Separate from the normal student create/update so editing a student's
 * contact details can never clear their placement.
 */
@Service
public class StudentPlacementService {

    private static final Pattern ROLL = Pattern.compile("^[A-Za-z0-9-]{1,20}$");

    private final AcademicStudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final SchoolClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final SchoolTenantGuard guard;
    private final CurrentTenantProvider tenantProvider;
    private final AuditService auditService;

    public StudentPlacementService(AcademicStudentRepository studentRepository, SectionRepository sectionRepository,
            SchoolClassRepository classRepository, AcademicYearRepository yearRepository, SchoolTenantGuard guard,
            CurrentTenantProvider tenantProvider, AuditService auditService) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.classRepository = classRepository;
        this.yearRepository = yearRepository;
        this.guard = guard;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public StudentPlacementResponse place(UUID studentId, PlaceStudentRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();

        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (!student.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STUDENT_INACTIVE", "That student is deactivated.");
        }
        Section section = sectionRepository.findByIdAndTenantId(request.sectionId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", request.sectionId()));
        if (!section.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECTION_INACTIVE", "That section is inactive.");
        }
        AcademicYear year = resolveYear(tenantId, request.academicYearId());
        String roll = normalizeRoll(request.rollNumber());

        boolean sameSection = section.getId().equals(student.getSectionId());
        boolean unchanged = sameSection && year.getId().equals(student.getAcademicYearId())
                && Objects.equals(roll, student.getRollNumber());

        // Repeating the same request changes (and audits) nothing.
        if (!unchanged) {
            if (!sameSection && section.getCapacity() != null
                    && studentRepository.countByTenantIdAndSectionIdAndActiveTrue(tenantId, section.getId()) >= section
                            .getCapacity()) {
                throw new ApiException(HttpStatus.CONFLICT, "SECTION_FULL",
                        "That section is full (" + section.getCapacity() + " students).");
            }
            if (roll != null && studentRepository
                    .existsByTenantIdAndSectionIdAndAcademicYearIdAndRollNumberAndActiveTrueAndIdNot(
                            tenantId, section.getId(), year.getId(), roll, student.getId())) {
                throw rollInUse(roll);
            }

            student.setSectionId(section.getId());
            student.setAcademicYearId(year.getId());
            student.setRollNumber(roll);
            try {
                studentRepository.saveAndFlush(student);
            } catch (DataIntegrityViolationException e) {
                // Lost a race with another request taking the same roll number.
                throw rollInUse(roll);
            }
            auditService.record("STUDENT_PLACED", "STUDENT", studentId,
                    AuditService.meta("sectionId", section.getId(), "academicYearId", year.getId(),
                            "rollNumber", roll));
        }
        SchoolClass schoolClass = classRepository.findByIdAndTenantId(section.getClassId(), tenantId).orElse(null);
        return toResponse(student, section, schoolClass, year);
    }

    /** Idempotent: an unplaced student stays unplaced. */
    @Transactional
    public void unplace(UUID studentId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (student.getSectionId() != null) {
            UUID previousSection = student.getSectionId();
            student.setSectionId(null);
            student.setRollNumber(null);
            student.setAcademicYearId(null);
            studentRepository.save(student);
            auditService.record("STUDENT_UNPLACED", "STUDENT", studentId,
                    AuditService.meta("sectionId", previousSection));
        }
    }

    /**
     * Active students of one section, in roll-number order (numeric rolls sort as
     * numbers).
     */
    public List<StudentPlacementResponse> roster(UUID sectionId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Section section = sectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        SchoolClass schoolClass = classRepository.findByIdAndTenantId(section.getClassId(), tenantId).orElse(null);
        Map<UUID, AcademicYear> years = yearRepository.findByTenantIdOrderByStartDateDesc(tenantId).stream()
                .collect(Collectors.toMap(AcademicYear::getId, Function.identity()));

        return studentRepository.findByTenantIdAndSectionIdAndActiveTrue(tenantId, sectionId).stream()
                .map(s -> toResponse(s, section, schoolClass, years.get(s.getAcademicYearId())))
                .sorted((a, b) -> {
                    int byRoll = compareRoll(a.rollNumber(), b.rollNumber());
                    return byRoll != 0 ? byRoll : a.fullName().compareToIgnoreCase(b.fullName());
                })
                .toList();
    }

    private AcademicYear resolveYear(UUID tenantId, UUID requestedYearId) {
        if (requestedYearId == null) {
            return yearRepository.findByTenantIdAndCurrentTrue(tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_CURRENT_YEAR",
                            "Set a current academic year first."));
        }
        return yearRepository.findByIdAndTenantId(requestedYearId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", requestedYearId));
    }

    private static String normalizeRoll(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String roll = raw.trim().toUpperCase(Locale.ROOT);
        if (!ROLL.matcher(roll).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLL_NUMBER",
                    "Roll number can use letters, digits and dashes only (max 20 characters).");
        }
        return roll;
    }

    private static ApiException rollInUse(String roll) {
        return new ApiException(HttpStatus.CONFLICT, "ROLL_NUMBER_IN_USE",
                "Roll number " + roll + " is already taken in that section for this year.");
    }

    private static int compareRoll(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return 1;
        }
        if (b == null) {
            return -1;
        }
        boolean aNumeric = a.chars().allMatch(Character::isDigit);
        boolean bNumeric = b.chars().allMatch(Character::isDigit);
        if (aNumeric && bNumeric) {
            int c = new BigInteger(a).compareTo(new BigInteger(b));
            if (c != 0) {
                return c;
            }
        } else if (aNumeric) {
            return -1;
        } else if (bNumeric) {
            return 1;
        }
        return a.compareTo(b);
    }

    private static StudentPlacementResponse toResponse(Student s, Section section, SchoolClass schoolClass,
            AcademicYear year) {
        return new StudentPlacementResponse(s.getId(), s.getFullName(), section.getId(), section.getName(),
                section.getClassId(), schoolClass == null ? null : schoolClass.getName(),
                year == null ? null : year.getId(), year == null ? null : year.getName(), s.getRollNumber());
    }
}
