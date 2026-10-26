package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.AcademicStudentRepository.SectionCount;
import com.teacheros.modules.academic.dto.AcademicDtos.ClassResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateClassRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateSectionRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.SectionResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateClassRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateSectionRequest;
import com.teacheros.modules.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClassSectionService {

    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final AcademicStudentRepository studentRepository;
    private final TeacherDirectory teachers;
    private final SchoolTenantGuard guard;
    private final CurrentTenantProvider tenantProvider;
    private final AuditService auditService;

    public ClassSectionService(SchoolClassRepository classRepository, SectionRepository sectionRepository,
            AcademicStudentRepository studentRepository, TeacherDirectory teachers, SchoolTenantGuard guard,
            CurrentTenantProvider tenantProvider, AuditService auditService) {
        this.classRepository = classRepository;
        this.sectionRepository = sectionRepository;
        this.studentRepository = studentRepository;
        this.teachers = teachers;
        this.guard = guard;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    // ---- Classes ----

    public List<ClassResponse> listClasses() {
        guard.assertSchool();
        return classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantProvider.getCurrentTenantId())
                .stream().map(ClassSectionService::toResponse).toList();
    }

    @Transactional
    public ClassResponse createClass(CreateClassRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        String name = request.name().trim();
        if (classRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_EXISTS",
                    "A class named \"" + name + "\" already exists.");
        }
        int order = request.displayOrder() != null ? request.displayOrder()
                : classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId).stream()
                        .mapToInt(SchoolClass::getDisplayOrder).max().orElse(0) + 1;
        SchoolClass created = classRepository.save(new SchoolClass(tenantId, name, order));
        auditService.record("SCHOOL_CLASS_CREATED", "SCHOOL_CLASS", created.getId(), AuditService.meta("name", name));
        return toResponse(created);
    }

    @Transactional
    public ClassResponse updateClass(UUID id, UpdateClassRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        SchoolClass schoolClass = classRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Class", id));
        String name = request.name().trim();
        if (classRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_EXISTS",
                    "A class named \"" + name + "\" already exists.");
        }
        if (Boolean.FALSE.equals(request.active()) && schoolClass.isActive()
                && sectionRepository.countByTenantIdAndClassIdAndActiveTrue(tenantId, id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_HAS_SECTIONS",
                    "Deactivate this class's sections first.");
        }
        schoolClass.setName(name);
        if (request.displayOrder() != null) {
            schoolClass.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            schoolClass.setActive(request.active());
        }
        return toResponse(classRepository.save(schoolClass));
    }

    // ---- Sections ----

    public List<SectionResponse> listSections(UUID classId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        List<Section> sections = classId == null
                ? sectionRepository.findByTenantId(tenantId)
                : sectionRepository.findByTenantIdAndClassId(tenantId, classId);
        return toResponses(tenantId, sections);
    }

    @Transactional
    public SectionResponse createSection(CreateSectionRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        SchoolClass schoolClass = classRepository.findByIdAndTenantId(request.classId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Class", request.classId()));
        if (!schoolClass.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLASS_INACTIVE", "That class is inactive.");
        }
        String name = request.name().trim();
        if (sectionRepository.existsByTenantIdAndClassIdAndNameIgnoreCase(tenantId, schoolClass.getId(), name)) {
            throw new ApiException(HttpStatus.CONFLICT, "SECTION_EXISTS",
                    "Section \"" + name + "\" already exists in " + schoolClass.getName() + ".");
        }
        if (request.classTeacherUserId() != null) {
            teachers.requireTeacher(tenantId, request.classTeacherUserId());
        }

        Section section = new Section(tenantId, schoolClass.getId(), name);
        section.setClassTeacherUserId(request.classTeacherUserId());
        section.setCapacity(request.capacity());
        section = sectionRepository.save(section);
        auditService.record("SECTION_CREATED", "SECTION", section.getId(),
                AuditService.meta("name", name, "classId", schoolClass.getId()));
        return toResponses(tenantId, List.of(section)).get(0);
    }

    @Transactional
    public SectionResponse updateSection(UUID id, UpdateSectionRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Section section = sectionRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));
        String name = request.name().trim();
        if (sectionRepository.existsByTenantIdAndClassIdAndNameIgnoreCaseAndIdNot(tenantId, section.getClassId(), name,
                id)) {
            throw new ApiException(HttpStatus.CONFLICT, "SECTION_EXISTS",
                    "Section \"" + name + "\" already exists in that class.");
        }
        if (request.classTeacherUserId() != null) {
            teachers.requireTeacher(tenantId, request.classTeacherUserId());
        }
        long enrolled = studentRepository.countByTenantIdAndSectionIdAndActiveTrue(tenantId, id);
        if (request.capacity() != null && request.capacity() < enrolled) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_BELOW_ENROLMENT",
                    "This section already has " + enrolled + " students.");
        }
        if (Boolean.FALSE.equals(request.active()) && section.isActive() && enrolled > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SECTION_HAS_STUDENTS",
                    "Move this section's students out before deactivating it.");
        }

        section.setName(name);
        section.setClassTeacherUserId(request.classTeacherUserId());
        section.setCapacity(request.capacity());
        if (request.active() != null) {
            section.setActive(request.active());
        }
        section = sectionRepository.save(section);
        return toResponses(tenantId, List.of(section)).get(0);
    }

    // One query each for classes, enrolment counts and teacher names, not one per
    // section.
    private List<SectionResponse> toResponses(UUID tenantId, List<Section> sections) {
        if (sections.isEmpty()) {
            return List.of();
        }
        Map<UUID, SchoolClass> classes = classRepository
                .findByTenantIdAndIdIn(tenantId, sections.stream().map(Section::getClassId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SchoolClass::getId, Function.identity()));
        Map<UUID, Long> counts = new HashMap<>();
        for (SectionCount c : studentRepository.countActiveBySection(tenantId)) {
            counts.put(c.getSectionId(), c.getStudentCount());
        }
        Map<UUID, String> teacherNames = teachers.displayNames(tenantId, sections.stream()
                .map(Section::getClassTeacherUserId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return sections.stream()
                .sorted(Comparator.comparingInt((Section s) -> classes.get(s.getClassId()).getDisplayOrder())
                        .thenComparing(s -> classes.get(s.getClassId()).getName())
                        .thenComparing(Section::getName))
                .map(s -> new SectionResponse(s.getId(), s.getClassId(), classes.get(s.getClassId()).getName(),
                        s.getName(), s.getClassTeacherUserId(), teacherNames.get(s.getClassTeacherUserId()),
                        s.getCapacity(), counts.getOrDefault(s.getId(), 0L), s.isActive()))
                .toList();
    }

    private static ClassResponse toResponse(SchoolClass c) {
        return new ClassResponse(c.getId(), c.getName(), c.getDisplayOrder(), c.isActive());
    }
}
