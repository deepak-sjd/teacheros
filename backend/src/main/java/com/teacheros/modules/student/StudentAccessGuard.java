package com.teacheros.modules.student;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single place enforcing "which student records can the current user see."
 *
 * Staff roles (OWNER/TEACHER/STAFF/ADMIN) can see any student in their own
 * tenant - tenant isolation alone (CurrentTenantProvider) already covers
 * them, this guard is a no-op for those roles.
 *
 * PARENT and STUDENT are different: tenant isolation is not enough, because
 * many students share a tenant. A parent may only see students whose
 * guardian_phone matches their own login phone; a student may only see
 * their own record. This is exactly the check that was missing before
 * PARENT/STUDENT roles had a real signup flow - do not skip it when adding
 * new student-scoped endpoints.
 *
 * Reused by StudentService, AttendanceService, and FeeService rather than
 * duplicated in each, per the "don't duplicate business logic" rule.
 */
@Component
public class StudentAccessGuard {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public StudentAccessGuard(
            StudentRepository studentRepository,
            UserRepository userRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    /**
     * Throws ResourceNotFoundException (not 403) if the current user isn't
     * allowed to view this student - a 404 doesn't confirm to an
     * unauthorized caller that the record even exists.
     */
    public void assertCanView(UUID studentId) {
        String role = currentTenantProvider.getCurrentRole();

        if (isStaffRole(role)) {
            return; // tenant isolation alone already scopes staff correctly
        }

        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        User currentUser = userRepository.findById(currentTenantProvider.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        boolean allowed = switch (role) {
            case "PARENT" -> currentUser.getPhoneNumber().equals(student.getGuardianPhone());
            case "STUDENT" -> currentUser.getPhoneNumber().equals(student.getPhoneNumber());
            default -> false;
        };

        if (!allowed) {
            throw new ResourceNotFoundException("Student", studentId);
        }
    }

    private boolean isStaffRole(String role) {
        return switch (role) {
            case "OWNER", "TEACHER", "STAFF", "ADMIN" -> true;
            default -> false;
        };
    }
}
