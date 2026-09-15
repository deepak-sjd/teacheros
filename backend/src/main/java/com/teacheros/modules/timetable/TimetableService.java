package com.teacheros.modules.timetable;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.timetable.dto.TimetableDtos.CreateSlotRequest;
import com.teacheros.modules.timetable.dto.TimetableDtos.SlotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TimetableService {

    private final TimetableSlotRepository timetableSlotRepository;
    private final BatchRepository batchRepository;
    private final BatchAccessGuard batchAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public TimetableService(
            TimetableSlotRepository timetableSlotRepository,
            BatchRepository batchRepository,
            BatchAccessGuard batchAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.batchRepository = batchRepository;
        this.batchAccessGuard = batchAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    @Transactional
    public SlotResponse create(CreateSlotRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLOT_TIME", "End time must be after start time");
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));

        TimetableSlot slot = new TimetableSlot(tenantId, request.batchId(), request.dayOfWeek(),
                request.startTime(), request.endTime(), request.subject());
        return toResponse(timetableSlotRepository.save(slot));
    }

    // Open to PARENT/STUDENT via the controller (no @PreAuthorize there) -
    // batchAccessGuard is what actually restricts it to a batch their
    // linked student is enrolled in. Same pattern as homework/study
    // material's batch-scoped reads.
    public List<SlotResponse> listForBatch(UUID batchId) {
        batchAccessGuard.assertCanView(batchId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return timetableSlotRepository.findByTenantIdAndBatchIdOrderByDayOfWeekAscStartTimeAsc(tenantId, batchId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID slotId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        timetableSlotRepository.findByIdAndTenantId(slotId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSlot", slotId));
        timetableSlotRepository.deleteByIdAndTenantId(slotId, tenantId);
    }

    private SlotResponse toResponse(TimetableSlot slot) {
        return new SlotResponse(slot.getId(), slot.getBatchId(), slot.getDayOfWeek(),
                slot.getStartTime(), slot.getEndTime(), slot.getSubject());
    }
}
