package com.teacheros.modules.announcement;

import com.teacheros.modules.announcement.dto.AnnouncementDtos.AnnouncementResponse;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.FeedAnnouncementResponse;
import com.teacheros.modules.announcement.dto.AnnouncementDtos.ReadStatsResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody CreateAnnouncementRequest request) {
        return ResponseEntity.ok(announcementService.create(request));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Page<AnnouncementResponse>> manage(Pageable pageable) {
        return ResponseEntity.ok(announcementService.listForStaff(pageable));
    }

    @GetMapping("/{id}/read-stats")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<ReadStatsResponse> readStats(@PathVariable UUID id) {
        return ResponseEntity.ok(announcementService.readStats(id));
    }

    // Audience scoping happens inside AnnouncementService (linked students'
    // batches/grades), not just by role - a PARENT only ever gets their own
    // children's announcements plus ALL.
    @GetMapping("/feed")
    @PreAuthorize("hasAnyRole('PARENT','STUDENT')")
    public ResponseEntity<Page<FeedAnnouncementResponse>> feed(Pageable pageable) {
        return ResponseEntity.ok(announcementService.feed(pageable));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('PARENT','STUDENT')")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        announcementService.markRead(id);
        return ResponseEntity.noContent().build();
    }
}
