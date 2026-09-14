package com.teacheros.modules.homework;

import com.teacheros.modules.homework.dto.HomeworkDtos.CreateHomeworkRequest;
import com.teacheros.modules.homework.dto.HomeworkDtos.HomeworkResponse;
import com.teacheros.modules.homework.dto.HomeworkDtos.UpdateHomeworkRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<HomeworkResponse> create(@Valid @RequestBody CreateHomeworkRequest request) {
        return ResponseEntity.ok(homeworkService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<HomeworkResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateHomeworkRequest request) {
        return ResponseEntity.ok(homeworkService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','TEACHER','STAFF','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        homeworkService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    // Open to any authenticated role - HomeworkService enforces batch-level
    // visibility for PARENT/STUDENT via BatchAccessGuard.
    public ResponseEntity<HomeworkResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(homeworkService.get(id));
    }

    @GetMapping
    // Same as above - BatchAccessGuard inside the service enforces who can
    // list which batch's homework, not a role check at the controller layer.
    public ResponseEntity<Page<HomeworkResponse>> listForBatch(
            @RequestParam UUID batchId, Pageable pageable) {
        return ResponseEntity.ok(homeworkService.listForBatch(batchId, pageable));
    }
}
