package com.teacheros.modules.material;

import com.teacheros.modules.material.dto.StudyMaterialDtos.DownloadUrlResponse;
import com.teacheros.modules.material.dto.StudyMaterialDtos.StudyMaterialResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/study-material")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    public StudyMaterialController(StudyMaterialService studyMaterialService) {
        this.studyMaterialService = studyMaterialService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<StudyMaterialResponse> upload(
            @RequestParam UUID batchId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(studyMaterialService.upload(batchId, title, description, file));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        studyMaterialService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download-url")
    // Open to any authenticated role - StudyMaterialService enforces
    // batch-level visibility for PARENT/STUDENT via BatchAccessGuard before
    // ever generating a signed URL.
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(studyMaterialService.getDownloadUrl(id));
    }

    @GetMapping
    public ResponseEntity<Page<StudyMaterialResponse>> listForBatch(
            @RequestParam UUID batchId, Pageable pageable) {
        return ResponseEntity.ok(studyMaterialService.listForBatch(batchId, pageable));
    }
}
