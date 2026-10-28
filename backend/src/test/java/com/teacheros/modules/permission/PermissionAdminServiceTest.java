package com.teacheros.modules.permission;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.PermissionMatrixResponse;
import com.teacheros.modules.permission.dto.PermissionAdminDtos.SetPermissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
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
class PermissionAdminServiceTest {

    private final UUID tenant = UUID.randomUUID();

    @Mock RolePermissionRepository repository;
    @Mock CurrentTenantProvider tenantProvider;
    @Mock AuditService auditService;
    PermissionAdminService service;

    @BeforeEach
    void setUp() {
        service = new PermissionAdminService(repository, tenantProvider, auditService);
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenant);
    }

    @Test
    void matrixShowsOwnerWithEverythingAndOnlyThisTenantsGrantsForOtherRoles() {
        when(repository.findByTenantId(tenant)).thenReturn(List.of(
                new RolePermission(tenant, "ACCOUNTANT", "FEES_VIEW"),
                new RolePermission(tenant, "ACCOUNTANT", "FEES_EDIT")));

        PermissionMatrixResponse m = service.getMatrix();

        assertThat(m.lockedRoles()).containsExactly("OWNER");
        assertThat(m.roles()).startsWith("OWNER").contains("ACCOUNTANT", "TEACHER");
        assertThat(m.grants().get("OWNER")).hasSize(Permission.values().length);
        assertThat(m.grants().get("ACCOUNTANT")).containsExactly("FEES_VIEW", "FEES_EDIT");
        assertThat(m.grants().get("COUNSELLOR")).isEmpty();
        assertThat(m.permissions()).hasSize(Permission.values().length);
    }

    @Test
    void grantingAMissingPermissionWritesItAndAuditsFromTo() {
        when(repository.existsByTenantIdAndRoleAndPermission(tenant, "ACCOUNTANT", "MARKS_VIEW_ALL")).thenReturn(false);

        var res = service.setGrant(new SetPermissionRequest("ACCOUNTANT", "MARKS_VIEW_ALL", true));

        assertThat(res.granted()).isTrue();
        verify(repository).grant(tenant, "ACCOUNTANT", "MARKS_VIEW_ALL");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(AuditActions.PERMISSION_CHANGED), eq(AuditActions.ROLE_PERMISSION),
                eq(null), meta.capture());
        assertThat(meta.getValue()).containsEntry("role", "ACCOUNTANT").containsEntry("permission", "MARKS_VIEW_ALL")
                .containsEntry("from", false).containsEntry("to", true);
    }

    @Test
    void revokingARemovesTheRowAndAudits() {
        when(repository.existsByTenantIdAndRoleAndPermission(tenant, "ACCOUNTANT", "FEES_EDIT")).thenReturn(true);

        var res = service.setGrant(new SetPermissionRequest("accountant", "fees_edit", false));

        assertThat(res.granted()).isFalse();
        verify(repository).revoke(tenant, "ACCOUNTANT", "FEES_EDIT");
        verify(auditService).record(eq(AuditActions.PERMISSION_CHANGED), any(), any(), any());
    }

    @Test
    void askingForTheStateAlreadyInPlaceChangesNothingAndWritesNoAudit() {
        when(repository.existsByTenantIdAndRoleAndPermission(tenant, "TEACHER", "FEES_VIEW")).thenReturn(true);

        service.setGrant(new SetPermissionRequest("TEACHER", "FEES_VIEW", true));

        verify(repository, never()).grant(any(), any(), any());
        verify(repository, never()).revoke(any(), any(), any());
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void ownerIsLockedAndOtherRolesAreRefused() {
        assertThatThrownBy(() -> service.setGrant(new SetPermissionRequest("OWNER", "FEES_EDIT", false)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("OWNER_LOCKED"));
        assertThatThrownBy(() -> service.setGrant(new SetPermissionRequest("PARENT", "FEES_EDIT", true)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ROLE_NOT_CONFIGURABLE"));
        assertThatThrownBy(() -> service.setGrant(new SetPermissionRequest("WIZARD", "FEES_EDIT", true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_ROLE"));
        assertThatThrownBy(() -> service.setGrant(new SetPermissionRequest("TEACHER", "FLY", true)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_PERMISSION"));
        verify(repository, never()).grant(any(), any(), any());
    }
}
