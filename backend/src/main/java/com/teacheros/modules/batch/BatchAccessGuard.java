package com.teacheros.modules.batch;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Single place enforcing "which batch's content can the current user see."
 * Sibling to StudentAccessGuard, but for batch-scoped content (homework
 * today; tests/timetable/announcements will reuse this same guard as they're
 * built, rather than each writing their own parent/student visibility
 * check).
 *
 * Staff roles: tenant isolation alone already scopes them correctly, this
 * guard is a no-op. PARENT/STUDENT: allowed only if they have at least one
 * linked student actually enrolled in that batch.
 */
@Component
public class BatchAccessGuard {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public BatchAccessGuard(
            StudentRepository studentRepository,
            UserRepository userRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    /**
     * Throws ResourceNotFoundException (not 403) if the current user isn't
     * allowed to view this batch's content - same reasoning as
     * StudentAccessGuard: a 404 doesn't confirm to an unauthorized caller
     * that the batch even exists.
     */
    public void assertCanView(UUID batchId) {
        String role = currentTenantProvider.getCurrentRole();

        if (isStaffRole(role)) {
            return;
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        User currentUser = userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));

        List<Student> linkedStudents = switch (role) {
            case "PARENT" -> studentRepository.findByTenantIdAndGuardianPhoneAndActiveTrue(
                    tenantId, currentUser.getPhoneNumber());
            case "STUDENT" -> studentRepository.findFirstByTenantIdAndPhoneNumberAndActiveTrue(
                    tenantId, currentUser.getPhoneNumber())
                    .map(List::of)
                    .orElse(List.of());
            default -> List.of();
        };

        boolean allowed = linkedStudents.stream().anyMatch(s -> batchId.equals(s.getBatchId()));

        if (!allowed) {
            throw new ResourceNotFoundException("Batch", batchId);
        }
    }

    private boolean isStaffRole(String role) {
        return switch (role) {
            case "OWNER", "TEACHER", "STAFF", "ADMIN" -> true;
            default -> false;
        };
    }
}
