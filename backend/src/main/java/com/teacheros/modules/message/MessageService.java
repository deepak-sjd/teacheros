package com.teacheros.modules.message;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.message.dto.MessageDtos.MessageResponse;
import com.teacheros.modules.message.dto.MessageDtos.SendMessageRequest;
import com.teacheros.modules.message.dto.MessageDtos.UnreadCountResponse;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * One thread per student (see V8 migration). Every method starts with
 * StudentAccessGuard#assertCanView, the same guard AttendanceService/
 * FeeService/AssessmentService already use - it's what lets a PARENT/
 * STUDENT only reach their own child's/own thread while staff can reach
 * any student in the tenant, without duplicating that check here.
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public MessageService(
            MessageRepository messageRepository,
            StudentAccessGuard studentAccessGuard,
            UserRepository userRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.messageRepository = messageRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.userRepository = userRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<MessageResponse> listThread(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        boolean viewerIsStaff = isStaffRole(currentTenantProvider.getCurrentRole());

        return messageRepository.findByTenantIdAndStudentIdOrderByCreatedAtAsc(tenantId, studentId).stream()
                .map(m -> toResponse(m, viewerIsStaff))
                .toList();
    }

    @Transactional
    public MessageResponse send(UUID studentId, SendMessageRequest request) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String role = currentTenantProvider.getCurrentRole();

        SenderRole senderRole;
        if (role.equals("PARENT")) {
            senderRole = SenderRole.PARENT;
        } else if (isStaffRole(role)) {
            senderRole = SenderRole.STAFF;
        } else {
            // A student can read the thread (StudentAccessGuard let them
            // this far) but can't send - the product spec only calls for
            // "parent messages teacher", not student messaging.
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_SEND",
                    "Only a parent or a staff member can send a message");
        }

        String senderName = userRepository.findById(currentTenantProvider.getCurrentUserId())
                .map(User::getDisplayName)
                .orElse(senderRole == SenderRole.PARENT ? "Parent" : "School");

        Message message = new Message(tenantId, studentId, senderRole, senderName, request.body());
        message = messageRepository.save(message);
        return toResponse(message, isStaffRole(role));
    }

    @Transactional
    public void markThreadRead(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        if (isStaffRole(currentTenantProvider.getCurrentRole())) {
            messageRepository.markReadByStaff(tenantId, studentId);
        } else {
            messageRepository.markReadByParent(tenantId, studentId);
        }
    }

    public UnreadCountResponse unreadCount(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        long count = isStaffRole(currentTenantProvider.getCurrentRole())
                ? messageRepository.countByTenantIdAndStudentIdAndReadByStaffFalse(tenantId, studentId)
                : messageRepository.countByTenantIdAndStudentIdAndReadByParentFalse(tenantId, studentId);
        return new UnreadCountResponse(count);
    }

    private MessageResponse toResponse(Message m, boolean viewerIsStaff) {
        boolean mine = viewerIsStaff
                ? m.getSenderRole() == SenderRole.STAFF
                : m.getSenderRole() == SenderRole.PARENT;
        return new MessageResponse(m.getId(), m.getSenderRole().name(), m.getSenderName(), m.getBody(), mine,
                m.getCreatedAt());
    }

    private boolean isStaffRole(String role) {
        return switch (role) {
            case "OWNER", "TEACHER", "STAFF", "ADMIN" -> true;
            default -> false;
        };
    }
}
