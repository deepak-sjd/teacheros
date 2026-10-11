package com.teacheros.modules.permission;

import com.teacheros.common.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Mock
    RolePermissionRepository repository;
    @Mock
    CurrentTenantProvider tenantProvider;
    PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(repository, tenantProvider);
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenantId);
    }

    @Test
    void ownerIsAlwaysAllowedWithoutHittingTheDatabase() {
        when(tenantProvider.getCurrentRole()).thenReturn("OWNER");

        assertTrue(service.has("FEES_EDIT"));
        verify(repository, never()).existsByTenantIdAndRoleAndPermission(any(), anyString(), anyString());
    }

    @Test
    void allowedWhenRowExistsForThisTenantAndRole() {
        when(tenantProvider.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(repository.existsByTenantIdAndRoleAndPermission(tenantId, "ACCOUNTANT", "FEES_EDIT")).thenReturn(true);

        assertTrue(service.has("FEES_EDIT"));
    }

    @Test
    void deniedWhenNoRowExists() {
        when(tenantProvider.getCurrentRole()).thenReturn("ACCOUNTANT");

        assertFalse(service.has("MARKS_EDIT"));
    }

    @Test
    void unknownPermissionNameFailsLoudlyInsteadOfAllowing() {
        when(tenantProvider.getCurrentRole()).thenReturn("TEACHER");

        assertThrows(IllegalArgumentException.class, () -> service.has("FEES_EDITT"));
    }
}
