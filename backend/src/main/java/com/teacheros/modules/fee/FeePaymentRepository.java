package com.teacheros.modules.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {
    List<FeePayment> findByTenantIdAndFeeId(UUID tenantId, UUID feeId);

    boolean existsByTenantIdAndGatewayPaymentId(UUID tenantId, String gatewayPaymentId);

    // Used by ReportService for the "collected this period" figure.
    // COALESCE guards the no-payments-yet case, where SUM(...) alone would
    // return NULL instead of zero.
    @Query("""
            SELECT COALESCE(SUM(p.amountPaid), 0) FROM FeePayment p
            WHERE p.tenantId = :tenantId AND p.paidAt >= :from AND p.paidAt < :to
            """)
    BigDecimal sumCollectedBetween(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Every payment in range, ungrouped - AnalyticsService buckets these
     * by calendar day (Asia/Kolkata, matching the rest of the app - see
     * DoubtService) to build the fee-collection trend chart. Unlike
     * attendance/marks, fees have no batchId in the schema (see
     * ReportDtos.FeeReportSummary's javadoc), so this is tenant-wide only -
     * there's no batch param to narrow it by.
     */
    @Query("""
            SELECT p.paidAt AS paidAt, p.amountPaid AS amountPaid
            FROM FeePayment p
            WHERE p.tenantId = :tenantId AND p.paidAt >= :from AND p.paidAt < :to
            """)
    List<PaymentRow> findPaymentsInRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    interface PaymentRow {
        Instant getPaidAt();

        BigDecimal getAmountPaid();
    }
}
