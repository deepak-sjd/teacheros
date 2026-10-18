package com.teacheros.modules.ai;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.ai.AiUsageRepository.DailyUsageRow;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiDailyUsageRow;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiFeatureSummary;
import com.teacheros.modules.ai.dto.AiUsageDtos.AiUsageDashboardResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Owner-facing AI usage numbers, computed on demand from ai_usage - nothing
 * stored, same rule as ReportService. Always scoped to the caller's tenant.
 */
@Service
public class AiUsageReportService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiUsageRepository repository;
    private final AiUsageService aiUsageService;
    private final CurrentTenantProvider currentTenantProvider;
    private final Clock clock;
    private final BigDecimal inputRatePerMillion;
    private final BigDecimal outputRatePerMillion;
    private final String billedProvider;
    private final String currency;

    @Autowired
    public AiUsageReportService(
            AiUsageRepository repository,
            AiUsageService aiUsageService,
            CurrentTenantProvider currentTenantProvider,
            @Value("${app.ai.cost.input-per-million-tokens:3.00}") BigDecimal inputRatePerMillion,
            @Value("${app.ai.cost.output-per-million-tokens:15.00}") BigDecimal outputRatePerMillion,
            @Value("${app.ai.cost.billed-provider:anthropic}") String billedProvider,
            @Value("${app.ai.cost.currency:USD}") String currency) {
        this(repository, aiUsageService, currentTenantProvider, inputRatePerMillion, outputRatePerMillion,
                billedProvider, currency, Clock.system(APP_ZONE));
    }

    // Test seam.
    AiUsageReportService(AiUsageRepository repository, AiUsageService aiUsageService,
            CurrentTenantProvider currentTenantProvider, BigDecimal inputRatePerMillion,
            BigDecimal outputRatePerMillion, String billedProvider, String currency, Clock clock) {
        this.repository = repository;
        this.aiUsageService = aiUsageService;
        this.currentTenantProvider = currentTenantProvider;
        this.inputRatePerMillion = inputRatePerMillion;
        this.outputRatePerMillion = outputRatePerMillion;
        this.billedProvider = billedProvider;
        this.currency = currency;
        this.clock = clock;
    }

    public AiUsageDashboardResponse report(int days) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        int span = Math.max(1, Math.min(days, 90));
        LocalDate to = LocalDate.now(clock.withZone(APP_ZONE));
        LocalDate from = to.minusDays(span - 1L);
        Instant fromInstant = from.atStartOfDay(APP_ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(APP_ZONE).toInstant();

        List<DailyUsageRow> rows = repository.aggregateByDay(tenantId, fromInstant, toInstant);

        // per feature: [calls, failed, inputTokens, outputTokens]
        Map<AiFeature, long[]> totals = new EnumMap<>(AiFeature.class);
        Map<AiFeature, BigDecimal> costs = new EnumMap<>(AiFeature.class);
        // per day, per feature: [calls, failed]
        Map<LocalDate, Map<AiFeature, long[]>> daily = new TreeMap<>();

        for (DailyUsageRow row : rows) {
            AiFeature feature = AiFeature.valueOf(row.getFeature());
            long calls = nz(row.getCalls());
            long failed = "FAILED".equals(row.getStatus()) ? calls : 0;
            long in = nz(row.getInputTokens());
            long out = nz(row.getOutputTokens());

            long[] t = totals.computeIfAbsent(feature, f -> new long[4]);
            t[0] += calls;
            t[1] += failed;
            t[2] += in;
            t[3] += out;

            costs.merge(feature, costOf(row.getProvider(), in, out), BigDecimal::add);

            long[] d = daily.computeIfAbsent(LocalDate.parse(row.getDay()), k -> new EnumMap<>(AiFeature.class))
                    .computeIfAbsent(feature, f -> new long[2]);
            d[0] += calls;
            d[1] += failed;
        }

        Instant startOfToday = to.atStartOfDay(APP_ZONE).toInstant();
        List<AiFeatureSummary> features = new ArrayList<>();
        long totalCalls = 0;
        long totalFailed = 0;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (AiFeature feature : AiFeature.values()) {
            long[] t = totals.getOrDefault(feature, new long[4]);
            BigDecimal cost = costs.getOrDefault(feature, BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
            long usedToday = repository.countByTenantIdAndFeatureAndCreatedAtGreaterThanEqual(tenantId, feature,
                    startOfToday);
            features.add(new AiFeatureSummary(feature.name(), feature.label(), t[0], t[1], rate(t[1], t[0]), t[2],
                    t[3], cost, usedToday, aiUsageService.dailyLimit(feature)));
            totalCalls += t[0];
            totalFailed += t[1];
            totalCost = totalCost.add(cost);
        }

        List<AiDailyUsageRow> dailyRows = new ArrayList<>();
        daily.forEach((date, byFeature) -> byFeature.forEach(
                (feature, c) -> dailyRows.add(new AiDailyUsageRow(date, feature.name(), c[0], c[1]))));

        return new AiUsageDashboardResponse(from, to, currency, totalCalls, totalFailed, rate(totalFailed, totalCalls),
                totalCost.setScale(4, RoundingMode.HALF_UP), features, dailyRows);
    }

    private BigDecimal costOf(String provider, long inputTokens, long outputTokens) {
        if (!billedProvider.equalsIgnoreCase(provider)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(inputTokens).multiply(inputRatePerMillion).divide(MILLION, 6, RoundingMode.HALF_UP)
                .add(BigDecimal.valueOf(outputTokens).multiply(outputRatePerMillion)
                        .divide(MILLION, 6, RoundingMode.HALF_UP));
    }

    private double rate(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private long nz(Long value) {
        return value == null ? 0 : value;
    }
}
