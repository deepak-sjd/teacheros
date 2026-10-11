package com.teacheros.modules.payment;

import com.teacheros.modules.fee.FeePayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByTenantIdAndGatewayOrderId(UUID tenantId, String gatewayOrderId);

    /**
     * Row-locked variant for verify(): two concurrent verify calls for the
     * same order (double-tap, Razorpay callback retry) serialize here, so
     * the second one sees status=PAID and credits nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM PaymentOrder o
            WHERE o.tenantId = :tenantId AND o.gatewayOrderId = :gatewayOrderId
            """)
    Optional<PaymentOrder> findForUpdate(@Param("tenantId") UUID tenantId,
            @Param("gatewayOrderId") String gatewayOrderId);

    /**
     * NOT tenant-scoped - webhook only. The webhook has no JWT, so the
     * tenant is read FROM this row (gateway_order_id is globally unique).
     * Never call this from a JWT-authenticated path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PaymentOrder o WHERE o.gatewayOrderId = :gatewayOrderId")
    Optional<PaymentOrder> findForUpdateByGatewayOrderId(@Param("gatewayOrderId") String gatewayOrderId);

    // ---- Reconciliation (all tenant-scoped) ----

    @Query("""
            SELECT o FROM PaymentOrder o
            WHERE o.tenantId = :tenantId AND o.status = com.teacheros.modules.payment.PaymentOrderStatus.PAID
            AND NOT EXISTS (SELECT 1 FROM FeePayment p
                            WHERE p.tenantId = o.tenantId AND p.gatewayOrderId = o.gatewayOrderId)
            """)
    List<PaymentOrder> findPaidWithoutPayment(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT p FROM FeePayment p
            WHERE p.tenantId = :tenantId AND p.gatewayOrderId IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM PaymentOrder o
                            WHERE o.tenantId = p.tenantId AND o.gatewayOrderId = p.gatewayOrderId
                            AND o.status = com.teacheros.modules.payment.PaymentOrderStatus.PAID)
            """)
    List<FeePayment> findPaymentsWithoutPaidOrder(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT o FROM PaymentOrder o
            WHERE o.tenantId = :tenantId
            AND o.status = com.teacheros.modules.payment.PaymentOrderStatus.CREATED
            AND o.createdAt < :before
            """)
    List<PaymentOrder> findStaleCreated(@Param("tenantId") UUID tenantId, @Param("before") Instant before);
}
