package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.dto.AcademicDtos.CreateSubjectRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.SubjectResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.SubjectTeacherResponse;
import com.teacheros.modules.academic.dto.AcademicDtos.UpdateSubjectRequest;
import com.teacheros.modules.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectTeacherRepository assignmentRepository;
    private final TeacherDirectory teachers;
    private final SchoolTenantGuard guard;
    private final CurrentTenantProvider tenantProvider;
    private final AuditService auditService;

    public SubjectService(SubjectRepository subjectRepository, SectionRepository sectionRepository,
            SectionSubjectTeacherRepository assignmentRepository, TeacherDirectory teachers,
            SchoolTenantGuard guard, CurrentTenantProvider tenantProvider, AuditService auditService) {
        this.subjectRepository = subjectRepository;
        this.sectionRepository = sectionRepository;
        this.assignmentRepository = assignmentRepository;
        this.teachers = teachers;
        this.guard = guard;
        this.tenantProvider = tenantProvider;
        this.auditService = auditService;
    }

    // ---- Subjects ----

    public List<SubjectResponse> list() {
        guard.assertSchool();
        return subjectRepository.findByTenantIdOrderByNameAsc(tenantProvider.getCurrentTenantId()).stream()
                .map(SubjectService::toResponse).toList();
    }

    @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        if (subjectRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBJECT_EXISTS",
                    "A subject named \"" + name + "\" already exists.");
        }
        if (code != null && subjectRepository.existsByTenantIdAndCodeIgnoreCase(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBJECT_CODE_EXISTS",
                    "The code " + code + " is already used.");
        }
        Subject subject = subjectRepository.save(new Subject(tenantId, name, code));
        auditService.record("SUBJECT_CREATED", "SUBJECT", subject.getId(), AuditService.meta("name", name));
        return toResponse(subject);
    }

    @Transactional
    public SubjectResponse update(UUID id, UpdateSubjectRequest request) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Subject subject = subjectRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        String name = request.name().trim();
        String code = normalizeCode(request.code());
        if (subjectRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBJECT_EXISTS",
                    "A subject named \"" + name + "\" already exists.");
        }
        if (code != null && subjectRepository.existsByTenantIdAndCodeIgnoreCaseAndIdNot(tenantId, code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "SUBJECT_CODE_EXISTS",
                    "The code " + code + " is already used.");
        }
        subject.setName(name);
        subject.setCode(code);
        if (request.active() != null) {
            subject.setActive(request.active());
        }
        return toResponse(subjectRepository.save(subject));
    }

    // ---- Subject teachers per section ----

    public List<SubjectTeacherResponse> listSubjectTeachers(UUID sectionId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        requireSection(tenantId, sectionId);

        List<SectionSubjectTeacher> rows = assignmentRepository.findByTenantIdAndSectionId(tenantId, sectionId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Subject> subjects = subjectRepository
                .findByTenantIdAndIdIn(tenantId,
                        rows.stream().map(SectionSubjectTeacher::getSubjectId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<UUID, String> names = teachers.displayNames(tenantId,
                rows.stream().map(SectionSubjectTeacher::getTeacherUserId).collect(Collectors.toSet()));

        return rows.stream()
                .filter(r -> subjects.containsKey(r.getSubjectId()))
                .map(r -> toResponse(r, subjects.get(r.getSubjectId()), names))
                .sorted(Comparator.comparing(SubjectTeacherResponse::subjectName))
                .toList();
    }

    /** Assigns, or replaces, the teacher for one subject in one section. */
    @Transactional
    public SubjectTeacherResponse assignTeacher(UUID sectionId, UUID subjectId, UUID teacherUserId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        Section section = requireSection(tenantId, sectionId);
        if (!section.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SECTION_INACTIVE", "That section is inactive.");
        }
        Subject subject = subjectRepository.findByIdAndTenantId(subjectId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));
        if (!subject.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT_INACTIVE", "That subject is inactive.");
        }
        var teacher = teachers.requireTeacher(tenantId, teacherUserId);

        SectionSubjectTeacher link = assignmentRepository
                .findByTenantIdAndSectionIdAndSubjectId(tenantId, sectionId, subjectId)
                .orElseGet(() -> new SectionSubjectTeacher(tenantId, sectionId, subjectId, teacherUserId));
        boolean changed = link.getId() == null || !teacherUserId.equals(link.getTeacherUserId());
        link.setTeacherUserId(teacherUserId);
        assignmentRepository.save(link);
        if (changed) {
            auditService.record("SUBJECT_TEACHER_ASSIGNED", "SECTION", sectionId,
                    AuditService.meta("subjectId", subjectId, "teacherUserId", teacherUserId));
        }
        return toResponse(link, subject, Map.of(teacher.getId(), teacher.getDisplayName()));
    }

    /** Idempotent: removing an assignment that isn't there is not an error. */
    @Transactional
    public void removeTeacher(UUID sectionId, UUID subjectId) {
        guard.assertSchool();
        UUID tenantId = tenantProvider.getCurrentTenantId();
        requireSection(tenantId, sectionId);
        assignmentRepository.deleteByTenantIdAndSectionIdAndSubjectId(tenantId, sectionId, subjectId);
    }

    private Section requireSection(UUID tenantId, UUID sectionId) {
        return sectionRepository.findByIdAndTenantId(sectionId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
    }

    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    private static SubjectResponse toResponse(Subject s) {
        return new SubjectResponse(s.getId(), s.getName(), s.getCode(), s.isActive());
    }

    private static SubjectTeacherResponse toResponse(SectionSubjectTeacher link, Subject subject,
            Map<UUID, String> teacherNames) {
        return new SubjectTeacherResponse(subject.getId(), subject.getName(), subject.getCode(),
                link.getTeacherUserId(), teacherNames.get(link.getTeacherUserId()));
    }
}
