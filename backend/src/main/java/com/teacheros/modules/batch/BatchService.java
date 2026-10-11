package com.teacheros.modules.batch;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.batch.dto.BatchDtos.BatchResponse;
import com.teacheros.modules.batch.dto.BatchDtos.CreateBatchRequest;
import com.teacheros.modules.batch.dto.BatchDtos.UpdateBatchRequest;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final StudentRepository studentRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;

    public BatchService(BatchRepository batchRepository, StudentRepository studentRepository,
            CurrentTenantProvider currentTenantProvider, AuditService auditService) {
        this.batchRepository = batchRepository;
        this.studentRepository = studentRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
    }

    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = new Batch(tenantId, request.name(), request.subject(), request.scheduleSummary());
        batch = batchRepository.save(batch);
        return toResponse(batch);
    }

    @Transactional
    public BatchResponse update(UUID batchId, UpdateBatchRequest request) {
        Batch batch = requireActiveBatch(batchId);
        batch.setName(request.name());
        batch.setSubject(request.subject());
        batch.setScheduleSummary(request.scheduleSummary());
        return toResponse(batchRepository.save(batch));
    }

    /**
     * Soft delete (is_active=false) - homework, tests, attendance history
     * etc. all reference this batch by id and must stay readable. Refused
     * while active students are still enrolled, rather than silently
     * un-enrolling them. Idempotent for an already-inactive batch (and only
     * audited when something actually changed).
     */
    @Transactional
    public void deactivate(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (!batch.isActive()) {
            return;
        }

        long activeStudents = studentRepository.countByTenantIdAndBatchIdAndActiveTrue(tenantId, batchId);
        if (activeStudents > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BATCH_HAS_STUDENTS",
                    "This batch still has " + activeStudents + " active student"
                            + (activeStudents == 1 ? "" : "s")
                            + ". Move them to another batch or deactivate them first.");
        }

        batch.setActive(false);
        batchRepository.save(batch);
        auditService.record(AuditActions.BATCH_DEACTIVATED, AuditActions.BATCH, batchId,
                AuditService.meta("name", batch.getName(), "subject", batch.getSubject()));
    }

    public Page<BatchResponse> list(Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return batchRepository.findByTenantIdAndActiveTrue(tenantId, pageable).map(this::toResponse);
    }

    public BatchResponse get(UUID batchId) {
        return toResponse(requireActiveBatch(batchId));
    }

    private Batch requireActiveBatch(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        if (!batch.isActive()) {
            throw new ResourceNotFoundException("Batch", batchId);
        }
        return batch;
    }

    private BatchResponse toResponse(Batch batch) {
        return new BatchResponse(batch.getId(), batch.getName(), batch.getSubject(), batch.getScheduleSummary());
    }
}
