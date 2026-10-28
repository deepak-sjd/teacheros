package com.teacheros.modules.course;

import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadRequest;
import com.teacheros.modules.course.dto.VideoUploadDtos.CreateUploadResponse;
import com.teacheros.modules.course.dto.VideoUploadDtos.VideoLibraryResponse;
import com.teacheros.modules.course.dto.VideoUploadDtos.VideoUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Staff-only video library. Students never reach this: they only ever receive
// a signed playback URL from LearningController, after the enrolment check.
@RestController
@RequestMapping("/api/v1/video-uploads")
@PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
public class VideoUploadController {

    private final VideoUploadService uploadService;

    public VideoUploadController(VideoUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @GetMapping
    public ResponseEntity<VideoLibraryResponse> library() {
        return ResponseEntity.ok(uploadService.library());
    }

    @PostMapping
    public ResponseEntity<CreateUploadResponse> create(@Valid @RequestBody CreateUploadRequest request) {
        return ResponseEntity.ok(uploadService.create(request));
    }

    // POST because it pulls fresh state from the provider and writes it back.
    @PostMapping("/{id}/refresh")
    public ResponseEntity<VideoUploadResponse> refresh(@PathVariable UUID id) {
        return ResponseEntity.ok(uploadService.refresh(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        uploadService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
