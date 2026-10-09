package com.teacheros.modules.student;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
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

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StudentAccessGuard studentAccessGuard;

    public StudentService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            BatchRepository batchRepository,
            CurrentTenantProvider currentTenantProvider,
            StudentAccessGuard studentAccessGuard) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
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
     * linking. Idempotent.
     */
    @Transactional
    public void deactivate(UUID studentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        if (student.isActive()) {
            student.setActive(false);
            studentRepository.save(student);
        }
    }

    public Page<StudentResponse> list(UUID batchId, Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Page<Student> page = (batchId != null)
                ? studentRepository.findByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId, pageable)
                : studentRepository.findByTenantIdAndActiveTrue(tenantId, pageable);
        return page.map(this::toResponse);
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

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(student.getId(), student.getFullName(), student.getGrade(),
                student.getGuardianPhone(), student.getPhoneNumber(), student.getGuardianEmail(),
                student.getBatchId());
    }
}
