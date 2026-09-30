package com.teacheros.modules.doubt;

import com.teacheros.modules.doubt.dto.DoubtDtos.AskDoubtRequest;
import com.teacheros.modules.doubt.dto.DoubtDtos.AskDoubtResponse;
import com.teacheros.modules.doubt.dto.DoubtDtos.DoubtThreadResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doubts")
public class DoubtController {

    private final DoubtService doubtService;

    public DoubtController(DoubtService doubtService) {
        this.doubtService = doubtService;
    }

    // No @PreAuthorize - same pattern as MessageController: DoubtService is
    // where PARENT/staff (read-only) vs STUDENT (can ask) is decided, and
    // every method starts with StudentAccessGuard#assertCanView.

    @GetMapping("/students/{studentId}")
    public ResponseEntity<DoubtThreadResponse> listThread(@PathVariable UUID studentId) {
        return ResponseEntity.ok(doubtService.listThread(studentId));
    }

    @PostMapping("/students/{studentId}/ask")
    public ResponseEntity<AskDoubtResponse> ask(@PathVariable UUID studentId,
            @Valid @RequestBody AskDoubtRequest request) {
        return ResponseEntity.ok(doubtService.ask(studentId, request));
    }
}
