package com.teacheros.modules.timetable;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchAccessGuard;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.timetable.dto.TimetableDtos.CreateSlotRequest;
import com.teacheros.modules.timetable.dto.TimetableDtos.SlotResponse;
import com.teacheros.modules.timetable.dto.TimetableDtos.UpdateMeetingLinkRequest;
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

        assertNoOverlap(tenantId, request.batchId(), request.dayOfWeek(), request);

        TimetableSlot slot = new TimetableSlot(tenantId, request.batchId(), request.dayOfWeek(),
                request.startTime(), request.endTime(), request.subject());
        applyMeetingLink(slot, request.meetingLink(), request.meetingPlatform());
        return toResponse(timetableSlotRepository.save(slot));
    }

    /**
     * Sets, replaces, or clears a slot's video-call link without touching
     * its day/time/subject. A blank meetingLink clears both fields (a
     * teacher moving a batch back to in-person only) rather than being
     * rejected as invalid input.
     */
    @Transactional
    public SlotResponse updateMeetingLink(UUID slotId, UpdateMeetingLinkRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        TimetableSlot slot = timetableSlotRepository.findByIdAndTenantId(slotId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSlot", slotId));

        applyMeetingLink(slot, request.meetingLink(), request.meetingPlatform());
        return toResponse(timetableSlotRepository.save(slot));
    }

    private void applyMeetingLink(TimetableSlot slot, String meetingLink, MeetingPlatform meetingPlatform) {
        if (meetingLink == null || meetingLink.isBlank()) {
            slot.setMeetingLink(null);
            slot.setMeetingPlatform(null);
            return;
        }

        String trimmed = meetingLink.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEETING_LINK",
                    "Meeting link must be a full URL starting with http:// or https://");
        }

        slot.setMeetingLink(trimmed);
        slot.setMeetingPlatform(meetingPlatform != null ? meetingPlatform : MeetingPlatform.OTHER);
    }

    /**
     * Rejects a new slot that overlaps an existing one for the same batch
     * and day. Two ranges [aStart,aEnd) and [bStart,bEnd) overlap exactly
     * when aStart < bEnd AND bStart < aEnd - the classic interval-overlap
     * check, not just an equality comparison.
     */
    private void assertNoOverlap(UUID tenantId, UUID batchId, int dayOfWeek, CreateSlotRequest request) {
        List<TimetableSlot> sameDaySlots = timetableSlotRepository
                .findByTenantIdAndBatchIdAndDayOfWeek(tenantId, batchId, dayOfWeek);

        boolean overlaps = sameDaySlots.stream()
                .anyMatch(existing -> request.startTime().isBefore(existing.getEndTime())
                        && existing.getStartTime().isBefore(request.endTime()));

        if (overlaps) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SLOT_OVERLAP",
                    "This time overlaps an existing slot for this batch on that day.");
        }
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
                slot.getStartTime(), slot.getEndTime(), slot.getSubject(),
                slot.getMeetingLink(), slot.getMeetingPlatform());
    }
}
