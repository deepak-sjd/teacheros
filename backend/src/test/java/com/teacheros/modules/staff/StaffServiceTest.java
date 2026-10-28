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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StaffServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock CurrentTenantProvider tenantProvider;
    @Mock AuditService auditService;
    StaffService service;

    @BeforeEach
    void setUp() {
        service = new StaffService(userRepository, refreshTokenRepository, tenantProvider, auditService);
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenant);
        when(tenantProvider.getCurrentUserId()).thenReturn(actorId);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        actAs("OWNER");
    }

    private void actAs(String role) {
        when(tenantProvider.getCurrentRole()).thenReturn(role);
    }

    private User user(Role role, boolean active) {
        User u = new User(tenant, "9199" + (int) (Math.random() * 1_000_000), "Person", role);
        u.setId(UUID.randomUUID());
        u.setActive(active);
        return u;
    }

    private void found(User u) {
        when(userRepository.findByIdAndTenantId(u.getId(), tenant)).thenReturn(Optional.of(u));
    }

    private static void assertApi(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- create ----

    @Test
    void ownerCreatesAnAccountantInTheirOwnTenantAndItIsAudited() {
        StaffResponse res = service.create(new CreateStaffRequest(" Asha ", "919876543210", "accountant"));

        assertThat(res.role()).isEqualTo(Role.ACCOUNTANT);
        assertThat(res.displayName()).isEqualTo("Asha");
        assertThat(res.active()).isTrue();
        verify(userRepository).save(any(User.class));
        verify(auditService).record(eq(AuditActions.STAFF_CREATED), eq(AuditActions.STAFF), any(), any());
    }

    @Test
    void adminCannotCreateAnOwner() {
        actAs("ADMIN");
        assertApi(() -> service.create(new CreateStaffRequest("X", "919876543210", "OWNER")),
                HttpStatus.FORBIDDEN, "OWNER_ONLY");
        verify(userRepository, never()).save(any());
    }

    @Test
    void parentAndStudentAreNotStaffRoles() {
        assertApi(() -> service.create(new CreateStaffRequest("X", "919876543210", "PARENT")),
                HttpStatus.BAD_REQUEST, "INVALID_STAFF_ROLE");
        assertApi(() -> service.create(new CreateStaffRequest("X", "919876543210", "nonsense")),
                HttpStatus.BAD_REQUEST, "INVALID_STAFF_ROLE");
    }

    @Test
    void phoneAlreadyInThisTenantIsRejected() {
        when(userRepository.findByPhoneNumberAndTenantId("919876543210", tenant))
                .thenReturn(Optional.of(user(Role.PARENT, true)));
        assertApi(() -> service.create(new CreateStaffRequest("X", "919876543210", "TEACHER")),
                HttpStatus.CONFLICT, "PHONE_ALREADY_EXISTS");
    }

    @Test
    void phoneActiveInAnotherTenantIsRejectedBecauseLoginWouldBeAmbiguous() {
        when(userRepository.existsByPhoneNumberAndActiveTrue("919876543210")).thenReturn(true);
        assertApi(() -> service.create(new CreateStaffRequest("X", "919876543210", "TEACHER")),
                HttpStatus.CONFLICT, "PHONE_IN_USE");
        verify(userRepository, never()).save(any());
    }

    // ---- change role ----

    @Test
    void ownerChangesAnotherUsersRoleAndItIsAudited() {
        User target = user(Role.TEACHER, true);
        found(target);

        StaffResponse res = service.changeRole(target.getId(), new ChangeStaffRoleRequest("ACCOUNTANT"));

        assertThat(res.role()).isEqualTo(Role.ACCOUNTANT);
        verify(auditService).record(eq(AuditActions.STAFF_ROLE_CHANGED), eq(AuditActions.STAFF),
                eq(target.getId()), any());
    }

    @Test
    void sameRoleIsANoOpWithNoAuditRow() {
        User target = user(Role.TEACHER, true);
        found(target);

        service.changeRole(target.getId(), new ChangeStaffRoleRequest("TEACHER"));

        verify(userRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void nobodyCanChangeTheirOwnRole() {
        User self = user(Role.ADMIN, true);
        self.setId(actorId);
        found(self);
        actAs("ADMIN");

        assertApi(() -> service.changeRole(actorId, new ChangeStaffRoleRequest("OWNER")),
                HttpStatus.FORBIDDEN, "CANNOT_CHANGE_OWN_ROLE");
    }

    @Test
    void adminCannotDemoteOrPromoteToOwner() {
        actAs("ADMIN");
        User owner = user(Role.OWNER, true);
        found(owner);
        assertApi(() -> service.changeRole(owner.getId(), new ChangeStaffRoleRequest("TEACHER")),
                HttpStatus.FORBIDDEN, "OWNER_ONLY");

        User teacher = user(Role.TEACHER, true);
        found(teacher);
        assertApi(() -> service.changeRole(teacher.getId(), new ChangeStaffRoleRequest("OWNER")),
                HttpStatus.FORBIDDEN, "OWNER_ONLY");
    }

    @Test
    void theLastActiveOwnerCannotBeDemoted() {
        User onlyOwner = user(Role.OWNER, true);
        found(onlyOwner);
        when(userRepository.lockActiveByTenantIdAndRole(tenant, Role.OWNER)).thenReturn(List.of(onlyOwner));

        assertApi(() -> service.changeRole(onlyOwner.getId(), new ChangeStaffRoleRequest("ADMIN")),
                HttpStatus.CONFLICT, "LAST_OWNER");
        verify(userRepository, never()).save(any());
    }

    @Test
    void anOwnerCanBeDemotedWhenAnotherActiveOwnerExists() {
        User a = user(Role.OWNER, true);
        User b = user(Role.OWNER, true);
        found(a);
        when(userRepository.lockActiveByTenantIdAndRole(tenant, Role.OWNER)).thenReturn(List.of(a, b));

        assertThat(service.changeRole(a.getId(), new ChangeStaffRoleRequest("ADMIN")).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void anotherTenantsUserIsNotFoundAndNothingChanges() {
        UUID foreign = UUID.randomUUID();
        when(userRepository.findByIdAndTenantId(foreign, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(foreign, new ChangeStaffRoleRequest("ADMIN")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivate(foreign)).isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void aParentAccountIsNotFoundThroughTheStaffApi() {
        User parent = user(Role.PARENT, true);
        found(parent);
        assertThatThrownBy(() -> service.deactivate(parent.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- deactivate / reactivate ----

    @Test
    void deactivatingRevokesEverySessionAndIsAudited() {
        User target = user(Role.ACCOUNTANT, true);
        found(target);
        when(refreshTokenRepository.revokeAllForUser(eq(target.getId()), any())).thenReturn(2);

        StaffResponse res = service.deactivate(target.getId());

        assertThat(res.active()).isFalse();
        verify(refreshTokenRepository).revokeAllForUser(eq(target.getId()), any());
        verify(auditService).record(eq(AuditActions.STAFF_DEACTIVATED), eq(AuditActions.STAFF),
                eq(target.getId()), any());
    }

    @Test
    void nobodyDeactivatesThemself() {
        User self = user(Role.OWNER, true);
        self.setId(actorId);
        found(self);
        assertApi(() -> service.deactivate(actorId), HttpStatus.FORBIDDEN, "CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void adminCannotDeactivateAnOwner() {
        actAs("ADMIN");
        User owner = user(Role.OWNER, true);
        found(owner);
        assertApi(() -> service.deactivate(owner.getId()), HttpStatus.FORBIDDEN, "OWNER_ONLY");
    }

    @Test
    void theLastActiveOwnerCannotBeDeactivated() {
        User onlyOwner = user(Role.OWNER, true);
        found(onlyOwner);
        when(userRepository.lockActiveByTenantIdAndRole(tenant, Role.OWNER)).thenReturn(List.of(onlyOwner));

        assertApi(() -> service.deactivate(onlyOwner.getId()), HttpStatus.CONFLICT, "LAST_OWNER");
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void deactivatingAnAlreadyInactiveUserChangesNothing() {
        User target = user(Role.TEACHER, false);
        found(target);
        service.deactivate(target.getId());
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reactivatingWorksAndIsAudited() {
        User target = user(Role.TEACHER, false);
        found(target);

        assertThat(service.reactivate(target.getId()).active()).isTrue();
        verify(auditService).record(eq(AuditActions.STAFF_REACTIVATED), eq(AuditActions.STAFF),
                eq(target.getId()), any());
    }

    @Test
    void reactivatingIsBlockedIfThePhoneNowBelongsToAnotherActiveAccount() {
        User target = user(Role.TEACHER, false);
        found(target);
        when(userRepository.existsByPhoneNumberAndActiveTrue(target.getPhoneNumber())).thenReturn(true);

        assertApi(() -> service.reactivate(target.getId()), HttpStatus.CONFLICT, "PHONE_IN_USE");
    }
}
