package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.PromotionPlanner.Row;
import com.teacheros.modules.academic.dto.AcademicDtos.AcademicYearResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateAcademicYearRequest;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionPreviewResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionRequest;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionResultResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionRow;
import com.teacheros.modules.academic.dto.PromotionDtos.SectionMapping;
import com.teacheros.modules.academic.dto.PromotionDtos.StudentActionOverride;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.student.Student;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Year-end promotion. preview() and apply() share buildPlan(), so what staff
 * reviewed is exactly what gets applied. preview() writes nothing. apply() is
 * one transaction and idempotent: it only picks up active students still in
 * the "from" year, and after a run none are.
 */
@Service
public class PromotionService {

    private static final Pattern YEAR_NAME = Pattern.compile("^([0-9]{4})-([0-9]{2}|[0-9]{4})$");

    private final AcademicStudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final SchoolClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final AcademicYearService yearService;
    private final SchoolTenantGuard guard;
    private final CurrentTenantProvider tenantProvider;
    private final AuditService auditService;

    public PromotionService(AcademicStudentRepository studentRepository, SectionRepository sectionRepository,
            SchoolClassRepository classRepository, AcademicYearRepository yearRepository,
            AcademicYearService yearService, SchoolTenantGuard guard, CurrentTenantProvider tenantProvider,
            AuditService auditService) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.classRepository = classRepository;
        this.yearRepository = yearRepository;
        this.yearService = yearService;
        this.guard = guard;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    private record Plan(AcademicYear from, AcademicYear existingTo, String proposedName, LocalDate proposedStart,
            LocalDate proposedEnd, List<Row> rows, List<String> warnings) {
    }

    // No @Transactional on purpose: the tenant-config read in the guard may
    // create a missing config row, which a read-only transaction would reject.
    public PromotionPreviewResponse preview(PromotionRequest request) {
        guard.assertSchool();
        Plan plan = buildPlan(request);

        List<PromotionRow> rows = plan.rows().stream().map(PromotionService::toRow).toList();
        AcademicYear to = plan.existingTo();
        return new PromotionPreviewResponse(
                plan.from().getId(), plan.from().getName(),
                to == null ? null : to.getId(),
                to != null ? to.getName() : plan.proposedName(),
                to != null ? to.getStartDate() : plan.proposedStart(),
                to != null ? to.getEndDate() : plan.proposedEnd(),
                to == null,
                count(plan.rows(), PromotionAction.PROMOTE), count(plan.rows(), PromotionAction.DETAIN),
                count(plan.rows(), PromotionAction.LEAVE),
                (int) plan.rows().stream().filter(r -> r.issue() != null).count(),
                rows, plan.warnings());
    }

    @Transactional
    public PromotionResultResponse apply(PromotionRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Plan plan = buildPlan(request);

        // Nothing left to move (e.g. this exact request was already applied):
        // change nothing - no new year, no current-year switch, no audit entry.
        if (plan.rows().isEmpty()) {
            AcademicYear existing = plan.existingTo();
            return new PromotionResultResponse(existing == null ? null : existing.getId(),
                    existing == null ? plan.proposedName() : existing.getName(), false, 0, 0, 0);
        }

        long issues = plan.rows().stream().filter(r -> r.issue() != null).count();
        if (issues > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMOTION_BLOCKED",
                    issues + " student(s) have no target section. Run the preview and choose a section for them.");
        }

        AcademicYear to = plan.existingTo();
        boolean created = false;
        if (to == null) {
            AcademicYearResponse made;
            try {
                made = yearService.create(new CreateAcademicYearRequest(
                        plan.proposedName(), plan.proposedStart(), plan.proposedEnd(), false));
            } catch (ApiException e) {
                if ("YEAR_OVERLAP".equals(e.getCode())) {
                    throw new ApiException(HttpStatus.CONFLICT, "NEXT_YEAR_OVERLAP", "The next year ("
                            + plan.proposedName()
                            + ") would overlap an existing year. Create the target year yourself and pass toAcademicYearId.");
                }
                throw e;
            }
            to = yearRepository.findByIdAndTenantId(made.id(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Academic year", made.id()));
            created = true;
            if (!Boolean.FALSE.equals(request.setNewYearCurrent())) {
                yearService.setCurrent(to.getId());
            }
        }

        List<Student> changed = new ArrayList<>();
        int promoted = 0;
        int detained = 0;
        int left = 0;
        for (Row row : plan.rows()) {
            Student student = row.student();
            switch (row.action()) {
                case PROMOTE -> {
                    student.setSectionId(row.toSection().getId());
                    student.setAcademicYearId(to.getId());
                    student.setRollNumber(null); // new class, new roll call
                    promoted++;
                }
                case DETAIN -> {
                    student.setAcademicYearId(to.getId());
                    student.setRollNumber(null);
                    detained++;
                }
                case LEAVE -> {
                    student.setActive(false);
                    left++;
                }
            }
            changed.add(student);
        }
        studentRepository.saveAll(changed);

        auditService.record("PROMOTION_APPLIED", "ACADEMIC_YEAR", to.getId(),
                AuditService.meta("fromAcademicYearId", plan.from().getId(), "promoted", promoted,
                        "detained", detained, "left", left));
        return new PromotionResultResponse(to.getId(), to.getName(), created, promoted, detained, left);
    }

    private Plan buildPlan(PromotionRequest request) {
        UUID tenantId = tenantProvider.getCurrentTenantId();
        AcademicYear from = yearRepository.findByIdAndTenantId(request.fromAcademicYearId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", request.fromAcademicYearId()));
        List<AcademicYear> years = yearRepository.findByTenantIdOrderByStartDateDesc(tenantId);

        AcademicYear existingTo;
        String proposedName = null;
        LocalDate proposedStart = null;
        LocalDate proposedEnd = null;
        if (request.toAcademicYearId() != null) {
            existingTo = years.stream().filter(y -> y.getId().equals(request.toAcademicYearId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Academic year", request.toAcademicYearId()));
            if (!existingTo.getStartDate().isAfter(from.getStartDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_YEAR",
                        "The target year must start after the year being promoted.");
            }
        } else {
            proposedName = nextYearName(from.getName());
            proposedStart = from.getStartDate().plusYears(1);
            proposedEnd = from.getEndDate().plusYears(1);
            String name = proposedName;
            existingTo = years.stream().filter(y -> y.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        }

        List<Section> allSections = sectionRepository.findByTenantId(tenantId);
        Map<UUID, Section> sectionsById = allSections.stream()
                .collect(Collectors.toMap(Section::getId, Function.identity()));
        List<SchoolClass> classes = classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId);
        Map<UUID, SchoolClass> classesById = classes.stream()
                .collect(Collectors.toMap(SchoolClass::getId, Function.identity()));

        Collection<UUID> scope;
        if (request.sectionIds() == null || request.sectionIds().isEmpty()) {
            scope = sectionsById.keySet();
        } else {
            for (UUID id : request.sectionIds()) {
                if (!sectionsById.containsKey(id)) {
                    throw new ResourceNotFoundException("Section", id);
                }
            }
            scope = new HashSet<>(request.sectionIds());
        }
        List<Student> students = scope.isEmpty()
                ? List.of()
                : studentRepository.findByTenantIdAndAcademicYearIdAndSectionIdInAndActiveTrue(
                        tenantId, from.getId(), scope);

        Map<UUID, UUID> mappings = new HashMap<>();
        if (request.sectionMappings() != null) {
            for (SectionMapping m : request.sectionMappings()) {
                Section target = sectionsById.get(m.toSectionId());
                if (target == null || !sectionsById.containsKey(m.fromSectionId())) {
                    throw new ResourceNotFoundException("Section",
                            target == null ? m.toSectionId() : m.fromSectionId());
                }
                if (!target.isActive()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SECTION_INACTIVE", "A target section is inactive.");
                }
                if (mappings.put(m.fromSectionId(), m.toSectionId()) != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_MAPPING",
                            "A section can be mapped to only one target.");
                }
            }
        }

        Set<UUID> inScope = students.stream().map(Student::getId).collect(Collectors.toSet());
        Map<UUID, PromotionAction> actions = new HashMap<>();
        if (request.studentActions() != null) {
            for (StudentActionOverride o : request.studentActions()) {
                if (!inScope.contains(o.studentId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_STUDENT",
                            "A student in studentActions is not part of this promotion.");
                }
                actions.put(o.studentId(), o.action());
            }
        }

        List<Row> rows = PromotionPlanner.plan(students, sectionsById, classesById, classes, mappings, actions);
        return new Plan(from, existingTo, proposedName, proposedStart, proposedEnd, rows,
                warnings(rows, classesById));
    }

    private List<String> warnings(List<Row> rows, Map<UUID, SchoolClass> classesById) {
        List<String> warnings = new ArrayList<>();
        Map<UUID, Integer> incoming = new HashMap<>();
        Map<UUID, Section> targets = new HashMap<>();
        for (Row r : rows) {
            if (r.toSection() != null) {
                incoming.merge(r.toSection().getId(), 1, Integer::sum);
                targets.put(r.toSection().getId(), r.toSection());
            }
        }
        for (Map.Entry<UUID, Integer> e : incoming.entrySet()) {
            Section s = targets.get(e.getKey());
            if (s.getCapacity() != null && e.getValue() > s.getCapacity()) {
                warnings.add(classesById.get(s.getClassId()).getName() + " " + s.getName() + " would receive "
                        + e.getValue() + " students from this run (capacity " + s.getCapacity() + ").");
            }
        }
        warnings.add("Roll numbers are cleared for promoted and detained students - assign new ones afterwards.");
        return warnings;
    }

    private static String nextYearName(String name) {
        Matcher m = YEAR_NAME.matcher(name);
        if (!m.matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DERIVE_YEAR",
                    "Cannot work out the next year from \"" + name + "\". Pass toAcademicYearId instead.");
        }
        int start = Integer.parseInt(m.group(1)) + 1;
        String endPart = m.group(2);
        String end = endPart.length() == 2
                ? String.format("%02d", (Integer.parseInt(endPart) + 1) % 100)
                : String.valueOf(Integer.parseInt(endPart) + 1);
        return start + "-" + end;
    }

    private static int count(List<Row> rows, PromotionAction action) {
        return (int) rows.stream().filter(r -> r.action() == action).count();
    }

    private static PromotionRow toRow(Row r) {
        Student s = r.student();
        return new PromotionRow(s.getId(), s.getFullName(), s.getRollNumber(),
                r.fromSection().getId(), r.fromSection().getName(), r.fromClass().getName(),
                r.toSection() == null ? null : r.toSection().getId(),
                r.toSection() == null ? null : r.toSection().getName(),
                r.toClass() == null ? null : r.toClass().getName(),
                r.action(), r.note(), r.issue());
    }
}
