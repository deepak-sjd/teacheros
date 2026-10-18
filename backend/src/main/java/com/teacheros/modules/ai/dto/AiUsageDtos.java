package com.teacheros.modules.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AiUsageDtos {

    public record AiFeatureSummary(
            String feature,
            String label,
            long calls,
            long failed,
            double failureRate,
            long inputTokens,
            long outputTokens,
            BigDecimal estimatedCost,
            long usedToday,
            int dailyLimit) {
    }

    public record AiDailyUsageRow(
            LocalDate date,
            String feature,
            long calls,
            long failed) {
    }

    public record AiUsageDashboardResponse(
            LocalDate from,
            LocalDate to,
            String costCurrency,
            long totalCalls,
            long totalFailed,
            double failureRate,
            BigDecimal estimatedCost,
            List<AiFeatureSummary> features,
            List<AiDailyUsageRow> daily) {
    }
}
