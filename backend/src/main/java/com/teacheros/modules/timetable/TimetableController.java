package com.teacheros.modules.timetable;

import com.teacheros.modules.timetable.dto.TimetableDtos.CreateSlotRequest;
import com.teacheros.modules.timetable.dto.TimetableDtos.SlotResponse;
import com.teacheros.modules.timetable.dto.TimetableDtos.UpdateMeetingLinkRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timetable")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<SlotResponse> create(@Valid @RequestBody CreateSlotRequest request) {
        return ResponseEntity.ok(timetableService.create(request));
    }

    // No @PreAuthorize - open to PARENT/STUDENT, guarded inside the service
    // via BatchAccessGuard (their linked student must be in this batch).
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<List<SlotResponse>> listForBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(timetableService.listForBatch(batchId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        timetableService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Separate from create/delete so a teacher can add, swap, or remove a
    // Zoom/Meet link on an existing slot without recreating it.
    @PatchMapping("/{id}/meeting-link")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<SlotResponse> updateMeetingLink(@PathVariable UUID id,
            @Valid @RequestBody UpdateMeetingLinkRequest request) {
        return ResponseEntity.ok(timetableService.updateMeetingLink(id, request));
    }
}
