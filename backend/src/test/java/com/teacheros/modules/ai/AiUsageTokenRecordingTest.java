package com.teacheros.modules.ai;

import com.teacheros.common.tenant.CurrentTenantProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AiUsageTokenRecordingTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ROW_ID = UUID.randomUUID();
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
        lenient().when(tenantProvider.getCurrentUserId()).thenReturn(UUID.randomUUID());
        lenient().when(repository.save(any(AiUsage.class))).thenAnswer(inv -> {
            AiUsage row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(ROW_ID);
            }
            savedRow = row;
            return row;
        });
        lenient().when(repository.findByIdAndTenantId(ROW_ID, TENANT)).thenAnswer(inv -> Optional.ofNullable(savedRow));
        service = new AiUsageService(repository, tenantProvider, txManager,
                new MockEnvironment().withProperty("app.ai.provider", "anthropic"), CLOCK);
    }

    @Test
    void tokensReportedDuringTheCall_areSavedOnTheRow() {
        service.track(AiFeature.DOUBT, () -> {
            AiTokenReporter.report(120, 340);
            return "answer";
        });

        assertEquals(120, savedRow.getInputTokens());
        assertEquals(340, savedRow.getOutputTokens());
    }

    @Test
    void aStaleCountFromAnEarlierCallOnTheSameThread_isNotInherited() {
        AiTokenReporter.report(999, 999); // leftover on this thread

        service.track(AiFeature.DOUBT, () -> "answer"); // client reports nothing

        assertNull(savedRow.getInputTokens());
        assertNull(savedRow.getOutputTokens());
    }
}
