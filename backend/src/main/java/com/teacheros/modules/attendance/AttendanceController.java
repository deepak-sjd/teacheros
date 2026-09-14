package com.teacheros.modules.attendance;

import com.teacheros.modules.attendance.dto.AttendanceDtos.AttendanceSummaryResponse;
import com.teacheros.modules.attendance.dto.AttendanceDtos.BatchSessionSummary;
import com.teacheros.modules.attendance.dto.AttendanceDtos.MarkAttendanceRequest;
import com.teacheros.modules.attendance.dto.AttendanceDtos.StudentAttendanceRecord;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<StudentAttendanceRecord>> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.markAttendance(request));
    }

    @GetMapping("/sessions/{classSessionId}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<StudentAttendanceRecord>> getSessionAttendance(
            @PathVariable UUID classSessionId) {
        return ResponseEntity.ok(attendanceService.getSessionAttendance(classSessionId));
    }

    // Open to any authenticated role - AttendanceService enforces "your own
    // child / yourself only" for PARENT/STUDENT via StudentAccessGuard.
    @GetMapping("/students/{studentId}/summary")
    public ResponseEntity<AttendanceSummaryResponse> getStudentSummary(@PathVariable UUID studentId) {
        return ResponseEntity.ok(attendanceService.getStudentAttendanceSummary(studentId));
    }

    @GetMapping("/batches/{batchId}/sessions")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<List<BatchSessionSummary>> listSessionsForBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(attendanceService.listSessionsForBatch(batchId));
    }
}
