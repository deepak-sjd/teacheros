package com.teacheros.modules.compliance;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.compliance.dto.ComplianceDtos.ErasureRequestResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Erasure workflow: request (guardian or staff) -> OWNER approves/rejects ->
 * on approval the erasure runs. Approval is committed on its own first, then
 * the erasure runs in a second transaction, so a failed erasure leaves an
 * APPROVED->FAILED request (re-approvable) instead of silently rolling back
 * the decision. Every step writes an audit_log row.
 */
@Service
public class ErasureService {

    private static final Logger log = LoggerFactory.getLogger(ErasureService.class);
    private static final Set<ErasureStatus> OPEN = EnumSet.of(ErasureStatus.PENDING, ErasureStatus.APPROVED,
            ErasureStatus.FAILED);

    private final ErasureRequestRepository requestRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;
    private final StudentErasureExecutor executor;
    private final TransactionTemplate tx;

    public ErasureService(ErasureRequestRepository requestRepository, StudentRepository studentRepository,
            StudentAccessGuard studentAccessGuard, CurrentTenantProvider currentTenantProvider,
            AuditService auditService, StudentErasureExecutor executor, PlatformTransactionManager txManager) {
        this.requestRepository = requestRepository;
        this.studentRepository = studentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
        this.executor = executor;
        this.tx = new TransactionTemplate(txManager);
    }

    @Transactional
    public ErasureRequestResponse request(UUID studentId, String reason) {
        String role = currentTenantProvider.getCurrentRole();
        if ("STUDENT".equals(role)) {
            throw new ResourceNotFoundException("Student", studentId);
        }
        studentAccessGuard.assertCanView(studentId); // 404 for another family's child
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        if (!student.isActive() && StudentErasureExecutor.ERASED_NAME.equals(student.getFullName())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ERASED",
                    "This student's data has already been erased.");
        }
        if (requestRepository.existsByTenantIdAndStudentIdAndStatusIn(tenantId, studentId, OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "ERASURE_ALREADY_REQUESTED",
                    "An erasure request for this student is already open.");
        }

        ErasureRequest saved = requestRepository.save(new ErasureRequest(tenantId, studentId,
                currentTenantProvider.getCurrentUserId(), role, reason));
        auditService.record(ComplianceAuditActions.ERASURE_REQUESTED, ComplianceAuditActions.ERASURE_REQUEST,
                saved.getId(), AuditService.meta("studentId", studentId, "requestedByRole", role));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequestResponse> listForStudent(UUID studentId) {
        if ("STUDENT".equals(currentTenantProvider.getCurrentRole())) {
            throw new ResourceNotFoundException("Student", studentId);
        }
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return requestRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<ErasureRequestResponse> list(ErasureStatus status, Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Page<ErasureRequest> page = status == null
                ? requestRepository.findByTenantId(tenantId, pageable)
                : requestRepository.findByTenantIdAndStatusIn(tenantId, Set.of(status), pageable);
        return page.map(this::toResponse);
    }

    public ErasureRequestResponse reject(UUID requestId, String note) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        tx.executeWithoutResult(s -> {
            ErasureRequest r = load(requestId, tenantId);
            if (r.getStatus() != ErasureStatus.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "Only a pending request can be rejected.");
            }
            r.setStatus(ErasureStatus.REJECTED);
            r.setDecidedByUserId(userId);
            r.setDecidedAt(Instant.now());
            r.setDecisionNote(note);
            requestRepository.save(r);
            auditService.record(ComplianceAuditActions.ERASURE_REJECTED, ComplianceAuditActions.ERASURE_REQUEST,
                    r.getId(), AuditService.meta("studentId", r.getStudentId()));
        });
        return toResponse(load(requestId, tenantId));
    }

    /** Not @Transactional on purpose - see class comment. */
    public ErasureRequestResponse approve(UUID requestId, String note) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        // Step 1 - record the decision (committed even if the erasure below fails).
        tx.executeWithoutResult(s -> {
            ErasureRequest r = load(requestId, tenantId);
            if (r.getStatus() != ErasureStatus.PENDING && r.getStatus() != ErasureStatus.FAILED) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING",
                        "Only a pending or failed request can be approved.");
            }
            r.setStatus(ErasureStatus.APPROVED);
            r.setDecidedByUserId(userId);
            r.setDecidedAt(Instant.now());
            r.setDecisionNote(note);
            r.setFailureReason(null);
            requestRepository.save(r);
            auditService.record(ComplianceAuditActions.ERASURE_APPROVED, ComplianceAuditActions.ERASURE_REQUEST,
                    r.getId(), AuditService.meta("studentId", r.getStudentId()));
        });

        // Step 2 - do the erasure; all-or-nothing together with its audit rows.
        try {
            tx.executeWithoutResult(s -> {
                ErasureRequest r = load(requestId, tenantId);
                StudentErasureExecutor.Result res = executor.erase(tenantId, r.getStudentId());
                UUID studentId = r.getStudentId();
                auditService.record(ComplianceAuditActions.ERASURE_DOCUMENTS_DELETED,
                        ComplianceAuditActions.STUDENT, studentId,
                        AuditService.meta("requestId", r.getId(), "count", res.documentsDeleted()));
                auditService.record(ComplianceAuditActions.ERASURE_COMMUNICATIONS_DELETED,
                        ComplianceAuditActions.STUDENT, studentId,
                        AuditService.meta("requestId", r.getId(), "rows", res.communicationRowsDeleted()));
                auditService.record(ComplianceAuditActions.ERASURE_STUDENT_ANONYMISED,
                        ComplianceAuditActions.STUDENT, studentId,
                        AuditService.meta("requestId", r.getId(), "studentLoginsDeactivated", res.usersDeactivated()));
                r.setStatus(ErasureStatus.COMPLETED);
                r.setCompletedAt(Instant.now());
                requestRepository.save(r);
                auditService.record(ComplianceAuditActions.ERASURE_COMPLETED, ComplianceAuditActions.ERASURE_REQUEST,
                        r.getId(), AuditService.meta("studentId", studentId));
            });
        } catch (RuntimeException e) {
            log.error("Erasure request {} failed: {}", requestId, e.getMessage(), e);
            tx.executeWithoutResult(s -> {
                ErasureRequest r = load(requestId, tenantId);
                r.setStatus(ErasureStatus.FAILED);
                r.setFailureReason("Erasure failed part-way and was rolled back. Check the server log, then retry.");
                requestRepository.save(r);
                auditService.record(ComplianceAuditActions.ERASURE_FAILED, ComplianceAuditActions.ERASURE_REQUEST,
                        r.getId(), AuditService.meta("studentId", r.getStudentId()));
            });
        }
        return toResponse(load(requestId, tenantId));
    }

    private ErasureRequest load(UUID id, UUID tenantId) {
        return requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ErasureRequest", id));
    }

    private ErasureRequestResponse toResponse(ErasureRequest r) {
        String name = studentRepository.findByIdAndTenantId(r.getStudentId(), r.getTenantId())
                .map(Student::getFullName).orElse(StudentErasureExecutor.ERASED_NAME);
        return new ErasureRequestResponse(r.getId(), r.getStudentId(), name, r.getStatus().name(),
                r.getRequestedByRole(), r.getReason(), r.getCreatedAt(), r.getDecidedAt(), r.getDecisionNote(),
                r.getCompletedAt(), r.getFailureReason());
    }
}
