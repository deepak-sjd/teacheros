package com.teacheros.modules.audit;

import com.teacheros.common.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditLogRepository repository;
    @Mock
    CurrentTenantProvider currentTenantProvider;

    @Test
    void recordsTenantActorActionAndResourceFromTheJwtContext() {
        UUID tenant = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID resource = UUID.randomUUID();
        when(currentTenantProvider.getCurrentTenantId()).thenReturn(tenant);
        when(currentTenantProvider.getCurrentUserId()).thenReturn(actor);

        new AuditService(repository, currentTenantProvider).record(
                AuditActions.FEE_CREATED, AuditActions.FEE, resource,
                AuditService.meta("amountDue", new BigDecimal("500.00")));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog row = captor.getValue();
        assertThat(row.getTenantId()).isEqualTo(tenant);
        assertThat(row.getActorUserId()).isEqualTo(actor);
        assertThat(row.getAction()).isEqualTo("FEE_CREATED");
        assertThat(row.getResourceType()).isEqualTo("FEE");
        assertThat(row.getResourceId()).isEqualTo(resource);
        assertThat(row.getMetadata()).containsEntry("amountDue", new BigDecimal("500.00"));
    }

    @Test
    void masksPhonesAndDropsSecrets() {
        Map<String, Object> clean = AuditService.sanitize(AuditService.meta(
                "guardianPhone", "+919876543210",
                "note", "9876543210",
                "otp", "123456",
                "verificationCode", "654321",
                "refreshToken", "abc",
                "title", "Unit Test 1"));

        assertThat(clean).containsEntry("guardianPhone", "*********3210");
        assertThat(clean).containsEntry("note", "******3210");
        assertThat(clean).containsEntry("title", "Unit Test 1");
        assertThat(clean).doesNotContainKeys("otp", "verificationCode", "refreshToken");
        assertThat(clean.values().toString()).doesNotContain("9876543210").doesNotContain("123456");
    }

    @Test
    void nullMetadataBecomesAnEmptyMap() {
        assertThat(AuditService.sanitize(null)).isEmpty();
    }
}
