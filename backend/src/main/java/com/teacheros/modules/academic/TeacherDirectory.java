package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the users a class/subject can be assigned to. A teacher must be
 * an active staff member of the SAME tenant - the message is deliberately
 * identical for "no such user" and "user in another tenant" so it can't be
 * used to probe other tenants' users.
 */
@Component
public class TeacherDirectory {

    private static final Set<String> STAFF_ROLES = Set.of("OWNER", "TEACHER", "STAFF", "ADMIN");

    private final UserRepository userRepository;

    public TeacherDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireTeacher(UUID tenantId, UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()) && u.isActive()
                        && STAFF_ROLES.contains(u.getRole().name()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEACHER",
                        "That user is not an active staff member of your school."));
    }

    public Map<UUID, String> displayNames(UUID tenantId, Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        for (User u : userRepository.findAllById(userIds)) {
            if (tenantId.equals(u.getTenantId())) {
                names.put(u.getId(), u.getDisplayName());
            }
        }
        return names;
    }
}
