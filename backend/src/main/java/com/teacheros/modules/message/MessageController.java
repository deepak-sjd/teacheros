package com.teacheros.modules.message;

import com.teacheros.modules.message.dto.MessageDtos.MessageResponse;
import com.teacheros.modules.message.dto.MessageDtos.SendMessageRequest;
import com.teacheros.modules.message.dto.MessageDtos.UnreadCountResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // No @PreAuthorize - MessageService#send is where PARENT vs STAFF vs
    // (rejected) STUDENT is decided, and every method starts with
    // StudentAccessGuard#assertCanView, same as FeeController/
    // AssessmentController's student-scoped endpoints.

    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<MessageResponse>> listThread(@PathVariable UUID studentId) {
        return ResponseEntity.ok(messageService.listThread(studentId));
    }

    @PostMapping("/students/{studentId}")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID studentId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(messageService.send(studentId, request));
    }

    @PatchMapping("/students/{studentId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID studentId) {
        messageService.markThreadRead(studentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/students/{studentId}/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(@PathVariable UUID studentId) {
        return ResponseEntity.ok(messageService.unreadCount(studentId));
    }
}
