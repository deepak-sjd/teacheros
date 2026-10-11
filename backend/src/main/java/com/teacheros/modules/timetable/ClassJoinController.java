package com.teacheros.modules.timetable;

import com.teacheros.modules.timetable.dto.ClassJoinDtos.JoinResponse;
import com.teacheros.modules.timetable.dto.ClassJoinDtos.SessionJoinsResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

// Sibling to TimetableController (same base path) - kept separate so the
// slot CRUD controller stays untouched.
@RestController
@RequestMapping("/api/v1/timetable")
public class ClassJoinController {

    private final ClassJoinService classJoinService;

    public ClassJoinController(ClassJoinService classJoinService) {
        this.classJoinService = classJoinService;
    }

    // STUDENT only - logs the caller's own join, never someone else's.
    @PostMapping("/slots/{slotId}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<JoinResponse> join(@PathVariable UUID slotId) {
        return ResponseEntity.ok(classJoinService.recordJoin(slotId));
    }

    // Staff only - classmates' join times are not something a parent or
    // student should see (same reasoning as the marks roster).
    @GetMapping("/slots/{slotId}/joins")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<SessionJoinsResponse> listJoins(
            @PathVariable UUID slotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(classJoinService.listJoins(slotId, date));
    }
}
