package com.teacheros.modules.ai;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.doubt.AiTutorClient.AiTutorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The single gate every AI provider call goes through (doubts, study plans,
 * content generator).
 *
 * Flow: reserve a PENDING ai_usage row (enforcing the tenant's daily limit for
 * that feature) -> make the call -> mark the row SUCCESS or FAILED, with the
 * provider's token counts if the client reported them (see AiTokenReporter).
 *
 * Reservation and completion each run in their OWN transaction (REQUIRES_NEW),
 * so:
 * - the row is committed BEFORE the slow provider call - the advisory lock is
 * held only for the count+insert, and a crash mid-call still leaves a counted
 * row;
 * - the row survives even if the caller's own transaction later rolls back
 * (e.g. DoubtService#ask rolls back the saved question on a 429).
 *
 * Limits are per tenant, per feature, and reset at midnight IST.
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final AiUsageRepository repository;
    private final CurrentTenantProvider currentTenantProvider;
    private final TransactionTemplate requiresNew;
    private final Clock clock;
    private final String provider;
    private final String model;
    private final Map<AiFeature, Integer> dailyLimits = new EnumMap<>(AiFeature.class);

    @Autowired
    public AiUsageService(AiUsageRepository repository, CurrentTenantProvider currentTenantProvider,
            PlatformTransactionManager transactionManager, Environment environment) {
        this(repository, currentTenantProvider, transactionManager, environment, Clock.system(APP_ZONE));
    }

    // Test seam.
    AiUsageService(AiUsageRepository repository, CurrentTenantProvider currentTenantProvider,
            PlatformTransactionManager transactionManager, Environment environment, Clock clock) {
        this.repository = repository;
        this.currentTenantProvider = currentTenantProvider;
        this.clock = clock;

        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.provider = environment.getProperty("app.ai.provider", "log");
        this.model = environment.getProperty("app.ai." + provider + ".model"); // null for "log"

        for (AiFeature feature : AiFeature.values()) {
            dailyLimits.put(feature, environment.getProperty(
                    "app.ai.limits." + feature.configKey(), Integer.class, feature.defaultDailyLimit()));
        }
    }

    /**
     * The per-tenant daily cap for a feature - shown on the owner's AI usage page.
     */
    public int dailyLimit(AiFeature feature) {
        return dailyLimits.get(feature);
    }

    /**
     * Reserves one call for the current tenant, or throws 429. The caller MUST
     * finish the returned Ticket (success/failure) - or use {@link #track}.
     */
    public Ticket checkAndRecord(AiFeature feature) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        UUID userId = currentTenantProvider.getCurrentUserId();
        int limit = dailyLimits.get(feature);
        Instant startOfDay = LocalDate.now(clock.withZone(APP_ZONE)).atStartOfDay(APP_ZONE).toInstant();

        UUID usageId = requiresNew.execute(status -> {
            repository.lockForReservation(tenantId + ":" + feature.name());
            long used = repository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(tenantId, feature,
                    startOfDay);
            if (used >= limit) {
                return null;
            }
            return repository.save(new AiUsage(tenantId, userId, feature, provider, model)).getId();
        });

        if (usageId == null) {
            log.warn("AI daily limit reached: tenant={} feature={} limit={}", tenantId, feature, limit);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_LIMIT_REACHED",
                    "Your school has used all " + limit + " " + feature.label()
                            + " for today. The limit resets at midnight IST - please try again tomorrow.");
        }
        return new Ticket(tenantId, usageId);
    }

    /**
     * Reserve, run the provider call, record the outcome, and rethrow any failure
     * unchanged.
     */
    public <T> T track(AiFeature feature, Supplier<T> call) {
        AiTokenReporter.clear(); // never inherit a previous call's counts on a pooled thread
        Ticket ticket = checkAndRecord(feature);
        try {
            T result = call.get();
            int[] tokens = AiTokenReporter.consume();
            ticket.success(tokens == null ? null : tokens[0], tokens == null ? null : tokens[1]);
            return result;
        } catch (RuntimeException e) {
            AiTokenReporter.clear();
            ticket.failure(e instanceof AiTutorException ? "PROVIDER_ERROR" : "INTERNAL_ERROR");
            throw e;
        }
    }

    private void complete(UUID tenantId, UUID usageId, AiUsageStatus status, String errorCode,
            Integer inputTokens, Integer outputTokens) {
        try {
            requiresNew.executeWithoutResult(tx -> repository.findByIdAndTenantId(usageId, tenantId)
                    .ifPresent(row -> {
                        row.setStatus(status);
                        row.setErrorCode(errorCode);
                        row.setInputTokens(inputTokens);
                        row.setOutputTokens(outputTokens);
                        row.setCompletedAt(Instant.now());
                        repository.save(row);
                    }));
        } catch (RuntimeException e) {
            // Bookkeeping must never turn a successful AI call into a failure for the user.
            // The row stays PENDING, which still counts toward the limit.
            log.error("Could not finalise ai_usage row {}: {}", usageId, e.getMessage(), e);
        }
    }

    public final class Ticket {
        private final UUID tenantId;
        private final UUID usageId;
        private boolean finished;

        private Ticket(UUID tenantId, UUID usageId) {
            this.tenantId = tenantId;
            this.usageId = usageId;
        }

        public void success() {
            success(null, null);
        }

        public void success(Integer inputTokens, Integer outputTokens) {
            finish(AiUsageStatus.SUCCESS, null, inputTokens, outputTokens);
        }

        public void failure(String errorCode) {
            finish(AiUsageStatus.FAILED, errorCode, null, null);
        }

        private void finish(AiUsageStatus status, String errorCode, Integer in, Integer out) {
            if (finished) {
                return;
            }
            finished = true;
            complete(tenantId, usageId, status, errorCode, in, out);
        }
    }
}
