package com.teacheros.modules.leave;

import com.teacheros.modules.leave.dto.LeaveRequestDtos.DecideLeaveRequest;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.LeaveRequestResponse;
import com.teacheros.modules.leave.dto.LeaveRequestDtos.SubmitLeaveRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    // Family only. LeaveRequestService also runs StudentAccessGuard, so a
    // parent can only submit for their own child (404 for anyone else's).
    @PostMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('PARENT','STUDENT')")
    public ResponseEntity<LeaveRequestResponse> submit(@PathVariable UUID studentId,
            @Valid @RequestBody SubmitLeaveRequest request) {
        return ResponseEntity.ok(leaveRequestService.submit(studentId, request));
    }

    // No @PreAuthorize - open to any authenticated role, guarded inside the
    // service via StudentAccessGuard ("your own child / yourself only").
    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<LeaveRequestResponse>> listForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(leaveRequestService.listForStudent(studentId));
    }

    // Staff who can mark attendance can also see and decide leave.
    @GetMapping("/pending")
    @PreAuthorize("@perm.has('ATTENDANCE_MARK')")
    public ResponseEntity<Page<LeaveRequestResponse>> pending(
            @RequestParam(required = false) UUID batchId, Pageable pageable) {
        return ResponseEntity.ok(leaveRequestService.listPending(batchId, pageable));
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("@perm.has('ATTENDANCE_MARK')")
    public ResponseEntity<LeaveRequestResponse> decide(@PathVariable UUID id,
            @Valid @RequestBody DecideLeaveRequest request) {
        return ResponseEntity.ok(leaveRequestService.decide(id, request));
    }
}
