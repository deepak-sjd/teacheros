package com.teacheros.modules.student;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.student.dto.StudentDtos.CreateStudentRequest;
import com.teacheros.modules.student.dto.StudentDtos.StudentResponse;
import com.teacheros.modules.student.dto.StudentDtos.UpdateStudentRequest;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StudentService {

    private static final int MAX_LOOKUP_IDS = 200;

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;

    public StudentService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider,
            StudentAccessGuard studentAccessGuard,
            AuditService auditService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        assertBatchBelongsToTenant(tenantId, request.batchId());
        assertPhoneAvailable(tenantId, request.phoneNumber(), null);

        Student student = new Student(tenantId, request.batchId(), request.fullName(),
                request.grade(), request.guardianPhone());
        student.setPhoneNumber(request.phoneNumber());
        student.setGuardianEmail(request.guardianEmail());
        student = studentRepository.save(student);
        return toResponse(student);
    }

    /**
     * Staff-only (see controller's @PreAuthorize) - a student/parent editing
     * their own contact details isn't supported yet, this is the teacher
     * correcting/completing a record, e.g. adding the student's own phone
     * after the fact so they can log in themselves.
     */
    @Transactional
    public StudentResponse update(UUID studentId, UpdateStudentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        assertBatchBelongsToTenant(tenantId, request.batchId());
        assertPhoneAvailable(tenantId, request.phoneNumber(), studentId);

        student.setFullName(request.fullName());
        student.setGrade(request.grade());
        student.setGuardianPhone(request.guardianPhone());
        student.setPhoneNumber(request.phoneNumber());
        student.setGuardianEmail(request.guardianEmail());
        student.setBatchId(request.batchId());
        student = studentRepository.save(student);
        return toResponse(student);
    }

    /**
     * Soft delete - attendance, marks, fees and messages keep referencing
     * this student, so the row stays. Inactive students disappear from
     * lists, rosters, reports, alerts, and from parent/student account
     * linking. Idempotent (and only audited when something actually changed).
     */
    @Transactional
    public void deactivate(UUID studentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (student.isActive()) {
            student.setActive(false);
            studentRepository.save(student);
            // Name only - phone numbers are deliberately not written to the audit log.
            auditService.record(AuditActions.STUDENT_DEACTIVATED, AuditActions.STUDENT, studentId,
                    AuditService.meta("fullName", student.getFullName(), "batchId", student.getBatchId()));
        }
    }

    /**
     * search (optional) matches a name or guardian-phone fragment,
     * case-insensitively, ordered by name. Blank/null keeps the original
     * unsearched behaviour.
     */
    public Page<StudentResponse> list(UUID batchId, String search, Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
            return studentRepository.search(tenantId, batchId, pattern, pageable).map(this::toResponse);
        }

        Page<Student> page = (batchId != null)
                ? studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId, pageable)
                : studentRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
        return page.map(this::toResponse);
    }

    /**
     * Staff-only: resolve a set of student IDs to records (e.g. names for a page of
     * fees).
     */
    public List<StudentResponse> lookup(List<UUID> ids) {
        if (ids.size() > MAX_LOOKUP_IDS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_IDS",
                    "Look up at most " + MAX_LOOKUP_IDS + " students at a time.");
        }
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return studentRepository.findByTenantIdAndIdIn(tenantId, ids.stream().distinct().toList())
                .stream().map(this::toResponse).toList();
    }

    /**
     * Any authenticated role can call this (see StudentController - no
     * PreAuthorize restricting it), but PARENT/STUDENT callers only ever
     * get their own linked student back - studentAccessGuard enforces that
     * before we touch anything else. Staff roles are unaffected.
     */
    public StudentResponse get(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        return toResponse(student);
    }

    /**
     * PARENT self-service: every active student in this tenant whose
     * guardian_phone matches the calling user's own phone - siblings
     * included, no separate linking step the teacher has to perform.
     */
    public List<StudentResponse> listMyChildren() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String phoneNumber = currentUserPhoneNumber();
        return studentRepository.findByTenantIdAndGuardianPhoneAndActiveTrue(tenantId, phoneNumber)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * STUDENT self-service: the one student record whose own phone_number
     * matches the calling user's login phone.
     */
    public StudentResponse getMyStudentRecord() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String phoneNumber = currentUserPhoneNumber();
        Student student = studentRepository.findFirstByTenantIdAndPhoneNumberAndActiveTrue(tenantId, phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "self"));
        return toResponse(student);
    }

    // A client-supplied batchId must belong to the caller's own tenant -
    // without this a student could be enrolled in another tenant's batch.
    private void assertBatchBelongsToTenant(UUID tenantId, UUID batchId) {
        if (batchId == null) {
            return;
        }
        batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
    }

    // uq_students_tenant_phone would otherwise surface as a raw 500.
    private void assertPhoneAvailable(UUID tenantId, String phoneNumber, UUID selfId) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }
        studentRepository.findFirstByTenantIdAndPhoneNumber(tenantId, phoneNumber)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "STUDENT_PHONE_IN_USE",
                            "Another student (possibly a deactivated one) already uses that phone number.");
                });
    }

    private String currentUserPhoneNumber() {
        User user = userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(
                        () -> new IllegalStateException("Authenticated user not found - this should never happen"));
        return user.getPhoneNumber();
    }

    // '!' is the ESCAPE character in StudentRepository#search.
    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(student.getId(), student.getFullName(), student.getGrade(),
                student.getGuardianPhone(), student.getPhoneNumber(), student.getGuardianEmail(),
                student.getBatchId());
    }
}
