package com.teacheros.modules.tenant;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantConfigResponse;
import com.teacheros.modules.tenant.dto.TenantDtos.UpdateTenantConfigRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantConfigServiceTest {

    @Mock
    TenantConfigRepository repository;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    TenantConfigService service;
    final UUID tenantA = UUID.randomUUID();
    final UUID tenantB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TenantConfigService(repository, currentTenantProvider);
    }

    private TenantConfig existing() {
        return new TenantConfig(tenantA, TenantType.COACHING, TenantModules.defaultsFor(TenantType.COACHING));
    }

    @Test
    void getReadsOnlyTheCurrentTenantsRow() {
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.of(existing()));

        TenantConfigResponse res = service.get();

        assertThat(res.tenantType()).isEqualTo(TenantType.COACHING);
        verify(repository).findByTenantId(tenantA);
        verify(repository, never()).findByTenantId(tenantB);
    }

    @Test
    void getCreatesADefaultRowWhenMissing() {
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.empty());
        when(repository.save(any(TenantConfig.class))).thenAnswer(i -> i.getArgument(0));

        TenantConfigResponse res = service.get();

        assertThat(res.tenantType()).isEqualTo(TenantType.COACHING);
        assertThat(res.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(res.enabledModules()).containsExactlyInAnyOrderElementsOf(TenantModules.ALL);
    }

    @Test
    void partialUpdateChangesOnlyTheGivenFields() {
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.of(existing()));
        when(repository.save(any(TenantConfig.class))).thenAnswer(i -> i.getArgument(0));

        TenantConfigResponse res = service.update(
                new UpdateTenantConfigRequest("school", null, null, null, "#112233", null));

        assertThat(res.tenantType()).isEqualTo(TenantType.SCHOOL);
        assertThat(res.primaryColor()).isEqualTo("#112233");
        assertThat(res.locale()).isEqualTo("en-IN");
        assertThat(res.enabledModules()).hasSameSizeAs(TenantModules.ALL);
    }

    @Test
    void emptyStringClearsAColour() {
        TenantConfig cfg = existing();
        cfg.setPrimaryColor("#112233");
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.of(cfg));
        when(repository.save(any(TenantConfig.class))).thenAnswer(i -> i.getArgument(0));

        TenantConfigResponse res = service.update(new UpdateTenantConfigRequest(null, null, null, null, "", null));

        assertThat(res.primaryColor()).isNull();
    }

    @Test
    void unknownModuleIsRejected() {
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.update(
                new UpdateTenantConfigRequest(null, List.of("FEES", "NOPE"), null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_MODULE"));
        verify(repository, never()).save(any());
    }

    @Test
    void badTimezoneAndBadTypeAreRejected() {
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenantA);
        when(repository.findByTenantId(tenantA)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.update(
                new UpdateTenantConfigRequest(null, null, null, "Mars/Phobos", null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_TIMEZONE"));
        assertThatThrownBy(() -> service.update(
                new UpdateTenantConfigRequest("UNIVERSITY", null, null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_TENANT_TYPE"));
    }

    @Test
    void createDefaultUsesTheGivenTenantIdWithoutAnAuthenticatedPrincipal() {
        when(repository.save(any(TenantConfig.class))).thenAnswer(i -> i.getArgument(0));

        service.createDefault(tenantB);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(c -> c.getTenantId().equals(tenantB)));
        verify(currentTenantProvider, never()).getCurrentTenantId();
    }
}
