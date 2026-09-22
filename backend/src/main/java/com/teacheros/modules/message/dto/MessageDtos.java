package com.teacheros.modules.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class MessageDtos {

    public record SendMessageRequest(
            @NotBlank @Size(max = 2000) String body) {
    }

    public record MessageResponse(
            UUID id,
            String senderRole,
            String senderName,
            String body,
            // Whether the CALLER's side sent this message - a parent's own
            // messages are "mine" when a parent/student views the thread, a
            // staff member's own messages are "mine" when staff views it.
            // Lets the frontend align bubbles without knowing the viewer's
            // role itself.
            boolean mine,
            Instant createdAt) {
    }

    public record UnreadCountResponse(long count) {
    }
}
