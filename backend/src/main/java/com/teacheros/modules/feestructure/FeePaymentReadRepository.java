package com.teacheros.modules.feestructure;

import com.teacheros.modules.fee.FeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read-only report queries over fee_payments. Dev 3 owns writes. */
public interface FeePaymentReadRepository extends JpaRepository<FeePayment, UUID> {

    @Query("""
            SELECT p.feeId AS feeId, p.amountPaid AS amountPaid, p.method AS method, p.paidAt AS paidAt
            FROM FeePayment p
            WHERE p.tenantId = :tenantId AND p.paidAt >= :from AND p.paidAt < :to
            ORDER BY p.paidAt
            """)
    List<PaymentReadRow> findInRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT p.feeId AS feeId, SUM(p.amountPaid) AS total
            FROM FeePayment p WHERE p.tenantId = :tenantId GROUP BY p.feeId
            """)
    List<PaidTotalRow> sumPaidByFee(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT p.feeId AS feeId, SUM(p.amountPaid) AS total
            FROM FeePayment p WHERE p.tenantId = :tenantId AND p.feeId IN :feeIds GROUP BY p.feeId
            """)
    List<PaidTotalRow> sumPaidForFees(@Param("tenantId") UUID tenantId, @Param("feeIds") Collection<UUID> feeIds);

    interface PaymentReadRow {
        UUID getFeeId();

        BigDecimal getAmountPaid();

        String getMethod();

        Instant getPaidAt();
    }

    interface PaidTotalRow {
        UUID getFeeId();

        BigDecimal getTotal();
    }
}
