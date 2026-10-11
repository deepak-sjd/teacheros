package com.teacheros.modules.ai;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.doubt.AiTutorClient.AiTutorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ROW_ID = UUID.randomUUID();

    // 00:30 IST on 10 March 2026 - start of that IST day is 2026-03-09T18:30:00Z.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-09T19:00:00Z"), ZoneOffset.UTC);

    @Mock
    AiUsageRepository repository;
    @Mock
    CurrentTenantProvider tenantProvider;
    @Mock
    PlatformTransactionManager txManager;

    private AiUsage savedRow;
    private AiUsageService service;

    @BeforeEach
    void setUp() {
        lenient().when(tenantProvider.getCurrentTenantId()).thenReturn(TENANT);
        lenient().when(tenantProvider.getCurrentUserId()).thenReturn(USER);
        lenient().when(repository.save(any(AiUsage.class))).thenAnswer(inv -> {
            AiUsage row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(ROW_ID);
            }
            savedRow = row;
            return row;
        });
        lenient().when(repository.findByIdAndTenantId(ROW_ID, TENANT)).thenAnswer(inv -> Optional.ofNullable(savedRow));

        MockEnvironment env = new MockEnvironment()
                .withProperty("app.ai.provider", "anthropic")
                .withProperty("app.ai.anthropic.model", "claude-test")
                .withProperty("app.ai.limits.study-plan", "2");
        service = new AiUsageService(repository, tenantProvider, txManager, env, CLOCK);
    }

    @Test
    void underLimit_reservesPendingRow_thenMarksSuccess() {
        when(count(AiFeature.STUDY_PLAN)).thenReturn(1L);

        String result = service.track(AiFeature.STUDY_PLAN, () -> "plan");

        assertEquals("plan", result);
        assertNotNull(savedRow);
        assertEquals(TENANT, savedRow.getTenantId());
        assertEquals(USER, savedRow.getUserId());
        assertEquals(AiFeature.STUDY_PLAN, savedRow.getFeature());
        assertEquals("anthropic", savedRow.getProvider());
        assertEquals("claude-test", savedRow.getModel());
        assertEquals(AiUsageStatus.SUCCESS, savedRow.getStatus());
        assertNull(savedRow.getErrorCode());
        assertNotNull(savedRow.getCompletedAt());
    }

    @Test
    void atLimit_throws429_andDoesNotCallProviderOrWriteARow() {
        when(count(AiFeature.STUDY_PLAN)).thenReturn(2L);
        boolean[] providerCalled = { false };

        ApiException ex = assertThrows(ApiException.class, () -> service.track(AiFeature.STUDY_PLAN, () -> {
            providerCalled[0] = true;
            return "plan";
        }));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("AI_DAILY_LIMIT_REACHED", ex.getCode());
        assertEquals(false, providerCalled[0]);
        verify(repository, never()).save(any());
    }

    @Test
    void dailyWindowStartsAtMidnightIst() {
        when(count(AiFeature.STUDY_PLAN)).thenReturn(0L);

        service.checkAndRecord(AiFeature.STUDY_PLAN);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(repository).countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(
                eq(TENANT), eq(AiFeature.STUDY_PLAN), since.capture());
        assertEquals(Instant.parse("2026-03-09T18:30:00Z"), since.getValue());
    }

    @Test
    void providerFailure_isRecordedAsFailed_andRethrown() {
        when(count(AiFeature.STUDY_PLAN)).thenReturn(0L);

        assertThrows(AiTutorException.class, () -> service.track(AiFeature.STUDY_PLAN, () -> {
            throw new AiTutorException("boom", null);
        }));

        assertEquals(AiUsageStatus.FAILED, savedRow.getStatus());
        assertEquals("PROVIDER_ERROR", savedRow.getErrorCode());
        assertNotNull(savedRow.getCompletedAt());
    }

    @Test
    void limitsAreIndependentPerFeature() {
        // STUDY_PLAN is capped at 2 in this test's config; DOUBT uses its own default
        // (2000).
        when(count(AiFeature.DOUBT)).thenReturn(5L);

        service.track(AiFeature.DOUBT, () -> "answer");

        assertEquals(AiFeature.DOUBT, savedRow.getFeature());
        assertEquals(AiUsageStatus.SUCCESS, savedRow.getStatus());
    }

    private long count(AiFeature feature) {
        return repository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(eq(TENANT), eq(feature), any());
    }

    // Mockito's when(...) helper, kept local so the stubbing lines above stay
    // short.
    private static <T> org.mockito.stubbing.OngoingStubbing<T> when(T call) {
        return org.mockito.Mockito.when(call);
    }
}
