package com.teacheros.modules.homework;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.homework.dto.HomeworkDtos.CreateHomeworkRequest;
import com.teacheros.modules.homework.dto.HomeworkDtos.HomeworkResponse;
import com.teacheros.modules.homework.dto.HomeworkDtos.UpdateHomeworkRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HomeworkService {

    private final HomeworkRepository homeworkRepository;
    private final BatchRepository batchRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public HomeworkService(
            HomeworkRepository homeworkRepository,
            BatchRepository batchRepository,
            BatchAccessGuard batchAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.homeworkRepository = homeworkRepository;
        this.batchRepository = batchRepository;
        this.batchAccessGuard = batchAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public HomeworkResponse create(CreateHomeworkRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();

        // Confirms the batch belongs to this tenant before creating homework
        // against it - same tenant-isolation check every create endpoint uses.
        batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));

        Homework homework = new Homework(
                tenantId, request.batchId(), request.title(), request.description(), request.dueDate(), userId);
        return toResponse(homeworkRepository.save(homework));
    }

    @Transactional
    public HomeworkResponse update(UUID homeworkId, UpdateHomeworkRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Homework homework = homeworkRepository.findByIdAndTenantId(homeworkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", homeworkId));

        homework.setTitle(request.title());
        homework.setDescription(request.description());
        homework.setDueDate(request.dueDate());
        return toResponse(homeworkRepository.save(homework));
    }

    @Transactional
    public void delete(UUID homeworkId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Homework homework = homeworkRepository.findByIdAndTenantId(homeworkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", homeworkId));
        homeworkRepository.delete(homework);
    }

    public HomeworkResponse get(UUID homeworkId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Homework homework = homeworkRepository.findByIdAndTenantId(homeworkId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Homework", homeworkId));
        batchAccessGuard.assertCanView(homework.getBatchId());
        return toResponse(homework);
    }

    public Page<HomeworkResponse> listForBatch(UUID batchId, Pageable pageable) {
        batchAccessGuard.assertCanView(batchId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return homeworkRepository.findByTenantIdAndBatchIdOrderByDueDateDesc(tenantId, batchId, pageable)
                .map(this::toResponse);
    }

    private HomeworkResponse toResponse(Homework homework) {
        return new HomeworkResponse(homework.getId(), homework.getBatchId(), homework.getTitle(),
                homework.getDescription(), homework.getDueDate(), homework.getCreatedAt(), homework.getUpdatedAt());
    }
}
