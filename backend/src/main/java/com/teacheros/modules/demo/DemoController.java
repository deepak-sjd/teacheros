package com.teacheros.modules.demo;

import com.teacheros.modules.demo.dto.DemoDtos.BookLeadRequest;
import com.teacheros.modules.demo.dto.DemoDtos.BookingResponse;
import com.teacheros.modules.demo.dto.DemoDtos.CreateSlotRequest;
import com.teacheros.modules.demo.dto.DemoDtos.MarkAttendanceRequest;
import com.teacheros.modules.demo.dto.DemoDtos.SlotResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demos")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STAFF','COUNSELLOR')")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody CreateSlotRequest request) {
        return ResponseEntity.ok(demoService.createSlot(request));
    }

    @GetMapping("/slots")
    public ResponseEntity<List<SlotResponse>> listSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(demoService.listSlots(from, to));
    }

    @GetMapping("/slots/{id}/bookings")
    public ResponseEntity<List<BookingResponse>> slotBookings(@PathVariable UUID id) {
        return ResponseEntity.ok(demoService.bookingsForSlot(id));
    }

    @PatchMapping("/slots/{id}/cancel")
    public ResponseEntity<SlotResponse> cancelSlot(@PathVariable UUID id) {
        return ResponseEntity.ok(demoService.cancelSlot(id));
    }

    @PostMapping("/leads/{leadId}/bookings")
    public ResponseEntity<BookingResponse> bookLead(@PathVariable UUID leadId,
            @Valid @RequestBody BookLeadRequest request) {
        return ResponseEntity.ok(demoService.bookLead(leadId, request));
    }

    @GetMapping("/leads/{leadId}/bookings")
    public ResponseEntity<List<BookingResponse>> leadBookings(@PathVariable UUID leadId) {
        return ResponseEntity.ok(demoService.bookingsForLead(leadId));
    }

    @PatchMapping("/bookings/{id}/attendance")
    public ResponseEntity<BookingResponse> markAttendance(@PathVariable UUID id,
            @Valid @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.ok(demoService.markAttendance(id, request));
    }

    @PatchMapping("/bookings/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(demoService.cancelBooking(id));
    }
}
