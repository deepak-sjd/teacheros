package com.teacheros.modules.batch;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.dto.BatchDtos.BatchResponse;
import com.teacheros.modules.batch.dto.BatchDtos.CreateBatchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public BatchService(BatchRepository batchRepository, CurrentTenantProvider currentTenantProvider) {
        this.batchRepository = batchRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public BatchResponse create(CreateBatchRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = new Batch(tenantId, request.name(), request.subject(), request.scheduleSummary());
        batch = batchRepository.save(batch);
        return toResponse(batch);
    }

    public Page<BatchResponse> list(Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return batchRepository.findByTenantIdAndActiveTrue(tenantId, pageable).map(this::toResponse);
    }

    public BatchResponse get(UUID batchId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Batch batch = batchRepository.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
        return toResponse(batch);
    }

    private BatchResponse toResponse(Batch batch) {
        return new BatchResponse(batch.getId(), batch.getName(), batch.getSubject(), batch.getScheduleSummary());
    }
}
