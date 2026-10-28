package com.teacheros.modules.staff;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.auth.RefreshTokenRepository;
import com.teacheros.modules.staff.dto.StaffDtos.ChangeStaffRoleRequest;
import com.teacheros.modules.staff.dto.StaffDtos.CreateStaffRequest;
import com.teacheros.modules.staff.dto.StaffDtos.StaffResponse;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Staff accounts for the caller's tenant (OWNER/ADMIN only - enforced on the
 * controller). A pre-created staff user simply logs in through the normal OTP
 * flow: AuthService finds the user by phone before it would ever provision a
 * new tenant, so they land in the right tenant with the right role.
 *
 * Rules, all enforced here (not only in the UI):
 * - tenant comes from the JWT; every lookup is findByIdAndTenantId, and a
 *   non-staff account (PARENT/STUDENT) is "not found" through this API
 * - nobody changes their own role or deactivates themself
 * - only an OWNER can create, promote, demote, deactivate or reactivate an
 *   OWNER, so an ADMIN can never take the school over
 * - the last active OWNER can't be demoted or deactivated (the owner rows are
 *   locked while counting, so two requests can't race past the check)
 * - deactivation revokes every refresh token at once; AuthService also refuses
 *   to refresh or log in an inactive user. An access token already issued
 *   keeps working until it expires (app.jwt.access-token-ttl-minutes).
 * - a role change reaches the person's token at their next refresh, for the
 *   same reason.
 *
 * Every change writes an audit row in the same transaction.
 */
@Service
public class StaffService {

    private static final Set<Role> STAFF_ROLES = EnumSet.of(
            Role.OWNER, Role.ADMIN, Role.TEACHER, Role.STAFF, Role.ACCOUNTANT, Role.COUNSELLOR);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;

    public StaffService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            CurrentTenantProvider currentTenantProvider, AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return userRepository.findByTenantIdAndRoleInOrderByDisplayNameAsc(tenantId, STAFF_ROLES).stream()
                .map(StaffService::toResponse)
                .toList();
    }

    @Transactional
    public StaffResponse create(CreateStaffRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Role role = parseStaffRole(request.role());
        if (role == Role.OWNER && !actorIsOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only an owner can add another owner.");
        }

        String phone = request.phoneNumber().trim();
        if (userRepository.findByPhoneNumberAndTenantId(phone, tenantId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS",
                    "This phone number already has an account in your school. If it's a deactivated staff "
                            + "member, reactivate them instead.");
        }
        if (userRepository.existsByPhoneNumberAndActiveTrue(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_IN_USE",
                    "This phone number is already registered with another account.");
        }

        User saved = userRepository.save(new User(tenantId, phone, request.displayName().trim(), role));
        auditService.record(AuditActions.STAFF_CREATED, AuditActions.STAFF, saved.getId(),
                AuditService.meta("displayName", saved.getDisplayName(), "phone", phone, "role", role.name()));
        return toResponse(saved);
    }

    @Transactional
    public StaffResponse changeRole(UUID userId, ChangeStaffRoleRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        User target = requireStaff(tenantId, userId);
        Role newRole = parseStaffRole(request.role());

        if (target.getId().equals(currentTenantProvider.getCurrentUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_CHANGE_OWN_ROLE", "You can't change your own role.");
        }
        if ((target.getRole() == Role.OWNER || newRole == Role.OWNER) && !actorIsOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only an owner can change an owner's role.");
        }
        Role oldRole = target.getRole();
        if (oldRole == newRole) {
            return toResponse(target);
        }
        if (oldRole == Role.OWNER) {
            assertNotLastActiveOwner(tenantId, target);
        }

        target.setRole(newRole);
        target = userRepository.save(target);
        auditService.record(AuditActions.STAFF_ROLE_CHANGED, AuditActions.STAFF, target.getId(),
                AuditService.meta("displayName", target.getDisplayName(), "fromRole", oldRole.name(),
                        "toRole", newRole.name()));
        return toResponse(target);
    }

    @Transactional
    public StaffResponse deactivate(UUID userId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        User target = requireStaff(tenantId, userId);

        if (target.getId().equals(currentTenantProvider.getCurrentUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_DEACTIVATE_SELF", "You can't deactivate yourself.");
        }
        assertActorMayManage(target);
        if (!target.isActive()) {
            return toResponse(target);
        }
        if (target.getRole() == Role.OWNER) {
            assertNotLastActiveOwner(tenantId, target);
        }

        target.setActive(false);
        target = userRepository.save(target);
        int revoked = refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());
        auditService.record(AuditActions.STAFF_DEACTIVATED, AuditActions.STAFF, target.getId(),
                AuditService.meta("displayName", target.getDisplayName(), "role", target.getRole().name(),
                        "sessionsRevoked", revoked));
        return toResponse(target);
    }

    @Transactional
    public StaffResponse reactivate(UUID userId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        User target = requireStaff(tenantId, userId);

        assertActorMayManage(target);
        if (target.isActive()) {
            return toResponse(target);
        }
        if (userRepository.existsByPhoneNumberAndActiveTrue(target.getPhoneNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_IN_USE",
                    "This phone number is now used by another active account, so this person can't be reactivated.");
        }

        target.setActive(true);
        target = userRepository.save(target);
        auditService.record(AuditActions.STAFF_REACTIVATED, AuditActions.STAFF, target.getId(),
                AuditService.meta("displayName", target.getDisplayName(), "role", target.getRole().name()));
        return toResponse(target);
    }

    // ---- helpers ----

    /** Tenant-scoped, and a PARENT/STUDENT account is "not found" through the staff API. */
    private User requireStaff(UUID tenantId, UUID userId) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member", userId));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new ResourceNotFoundException("Staff member", userId);
        }
        return user;
    }

    private Role parseStaffRole(String value) {
        Role role;
        try {
            role = Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STAFF_ROLE", "Unknown role: " + value);
        }
        if (!STAFF_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STAFF_ROLE",
                    role.name() + " is not a staff role.");
        }
        return role;
    }

    private boolean actorIsOwner() {
        return Role.OWNER.name().equals(currentTenantProvider.getCurrentRole());
    }

    private void assertActorMayManage(User target) {
        if (target.getRole() == Role.OWNER && !actorIsOwner()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_ONLY", "Only an owner can manage an owner.");
        }
    }

    /** Counts under a row lock so concurrent requests serialise on the owners. */
    private void assertNotLastActiveOwner(UUID tenantId, User target) {
        List<User> activeOwners = userRepository.lockActiveByTenantIdAndRole(tenantId, Role.OWNER);
        boolean targetIsActiveOwner = activeOwners.stream().anyMatch(o -> o.getId().equals(target.getId()));
        if (targetIsActiveOwner && activeOwners.size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER",
                    "This is the only active owner. Add another owner first.");
        }
    }

    private static StaffResponse toResponse(User u) {
        return new StaffResponse(u.getId(), u.getDisplayName(), u.getPhoneNumber(), u.getRole(), u.isActive(),
                u.getLastLoginAt(), u.getCreatedAt());
    }
}
