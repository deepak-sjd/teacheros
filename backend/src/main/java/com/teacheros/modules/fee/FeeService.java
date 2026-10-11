package com.teacheros.modules.fee;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.audit.AuditActions;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.fee.dto.FeeDtos.CreateFeeRequest;
import com.teacheros.modules.fee.dto.FeeDtos.FeeResponse;
import com.teacheros.modules.fee.dto.FeeDtos.RecordPaymentRequest;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Fee tracking only - no payment processing. A payment record here means
 * "the teacher told us cash/UPI/bank transfer was received," not that we
 * processed a transaction. See blueprint Section 27/28: real payment
 * collection is a distinct, later decision that needs a licensed provider.
 *
 * OVERDUE rule: unpaid balance and dueDate < today (Asia/Kolkata). It is
 * applied in deriveStatus (create/payment), by FeeOverdueScheduler (daily),
 * and by a tenant-scoped sweep at the top of listPending.
 *
 * Fee creation and every payment (manual or gateway) write an audit_log row.
 */
@Service
public class FeeService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private final FeeRepository feeRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StudentAccessGuard studentAccessGuard;
    private final AuditService auditService;

    public FeeService(FeeRepository feeRepository, FeePaymentRepository feePaymentRepository,
            CurrentTenantProvider currentTenantProvider, StudentAccessGuard studentAccessGuard,
            AuditService auditService) {
        this.feeRepository = feeRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
        this.auditService = auditService;
    }

    /**
     * "Today" for fee purposes - always India time, same as the rest of the app.
     */
    public static LocalDate today() {
        return LocalDate.now(APP_ZONE);
    }

    @Transactional
    public FeeResponse createFee(CreateFeeRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = new Fee(tenantId, request.studentId(), request.amountDue(), request.dueDate());
        // A fee created with an already-past due date is overdue immediately.
        fee.setStatus(deriveStatus(request.amountDue(), BigDecimal.ZERO, request.dueDate()));
        fee = feeRepository.save(fee);

        auditService.record(AuditActions.FEE_CREATED, AuditActions.FEE, fee.getId(), AuditService.meta(
                "studentId", fee.getStudentId(),
                "amountDue", fee.getAmountDue(),
                "dueDate", fee.getDueDate(),
                "status", fee.getStatus()));

        return toResponse(fee, BigDecimal.ZERO);
    }

    @Transactional
    public FeeResponse recordPayment(UUID feeId, RecordPaymentRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = feeRepository.findByIdAndTenantId(feeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee", feeId));

        if (request.amountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Payment amount must be positive");
        }

        feePaymentRepository.save(new FeePayment(tenantId, fee.getId(), request.amountPaid(), request.method()));

        BigDecimal totalPaid = sumPayments(tenantId, fee.getId());
        fee.setStatus(deriveStatus(fee.getAmountDue(), totalPaid, fee.getDueDate()));
        fee = feeRepository.save(fee);

        auditService.record(AuditActions.FEE_PAYMENT_RECORDED, AuditActions.FEE, fee.getId(), AuditService.meta(
                "studentId", fee.getStudentId(),
                "amountPaid", request.amountPaid(),
                "method", request.method(),
                "source", "manual",
                "resultingStatus", fee.getStatus()));

        return toResponse(fee, totalPaid);
    }

    /**
     * Called by PaymentService after Razorpay's signature check passes.
     * Idempotent on gatewayPaymentId (see recordGatewayPaymentForTenant).
     */
    @Transactional
    public FeeResponse recordGatewayPayment(UUID feeId, BigDecimal amountPaid, String method,
            String gatewayOrderId, String gatewayPaymentId) {
        return recordGatewayPaymentForTenant(currentTenantProvider.getCurrentTenantId(), feeId, amountPaid,
                method, gatewayOrderId, gatewayPaymentId);
    }

    /**
     * Same as above but with an explicit tenant - ONLY for the Razorpay
     * webhook, which has no JWT. The tenant must come from the stored
     * PaymentOrder row, never from the request.
     *
     * Idempotent: Razorpay's checkout can call the success callback more
     * than once for the same payment (retries, double-taps), and recording
     * the same gatewayPaymentId twice would double-credit the fee.
     */
    @Transactional
    public FeeResponse recordGatewayPaymentForTenant(UUID tenantId, UUID feeId, BigDecimal amountPaid,
            String method, String gatewayOrderId, String gatewayPaymentId) {
        Fee fee = feeRepository.findByIdAndTenantId(feeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee", feeId));

        if (feePaymentRepository.existsByTenantIdAndGatewayPaymentId(tenantId, gatewayPaymentId)) {
            // Already recorded from an earlier callback - return the
            // current state rather than erroring, so a retried callback
            // still gets a clean 200 instead of a confusing failure.
            // (No second audit row - nothing new happened.)
            return toResponse(fee, sumPayments(tenantId, fee.getId()));
        }

        FeePayment payment = new FeePayment(tenantId, fee.getId(), amountPaid, method);
        payment.setGatewayOrderId(gatewayOrderId);
        payment.setGatewayPaymentId(gatewayPaymentId);
        feePaymentRepository.save(payment);

        BigDecimal totalPaid = sumPayments(tenantId, fee.getId());
        fee.setStatus(deriveStatus(fee.getAmountDue(), totalPaid, fee.getDueDate()));
        fee = feeRepository.save(fee);

        auditService.record(AuditActions.FEE_PAYMENT_RECORDED, AuditActions.FEE, fee.getId(), AuditService.meta(
                "studentId", fee.getStudentId(),
                "amountPaid", amountPaid,
                "method", method,
                "source", "gateway",
                "gatewayOrderId", gatewayOrderId,
                "gatewayPaymentId", gatewayPaymentId,
                "resultingStatus", fee.getStatus()));

        return toResponse(fee, totalPaid);
    }

    public FeeResponse get(UUID feeId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = feeRepository.findByIdAndTenantId(feeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee", feeId));
        return toResponse(fee, sumPayments(tenantId, fee.getId()));
    }

    // Open to PARENT/STUDENT via the controller (no @PreAuthorize there) -
    // this guard call is what actually restricts it to their own child/self.
    public List<FeeResponse> listForStudent(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return feeRepository.findByTenantIdAndStudentId(tenantId, studentId).stream()
                .map(fee -> toResponse(fee, sumPayments(tenantId, fee.getId())))
                .toList();
    }

    public Page<FeeResponse> listPending(Pageable pageable) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        // Catch fees that crossed their due date since the last daily sweep.
        feeRepository.sweepOverdueForTenant(tenantId, today());
        return feeRepository
                .findByTenantIdAndStatusIn(tenantId, List.of(FeeStatus.PENDING, FeeStatus.PARTIALLY_PAID,
                        FeeStatus.OVERDUE), pageable)
                .map(fee -> toResponse(fee, sumPayments(tenantId, fee.getId())));
    }

    private BigDecimal sumPayments(UUID tenantId, UUID feeId) {
        return feePaymentRepository.findByTenantIdAndFeeId(tenantId, feeId).stream()
                .map(FeePayment::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private FeeStatus deriveStatus(BigDecimal amountDue, BigDecimal totalPaid, LocalDate dueDate) {
        if (totalPaid.compareTo(amountDue) >= 0) {
            return FeeStatus.PAID;
        }
        if (dueDate.isBefore(today())) {
            return FeeStatus.OVERDUE;
        }
        if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            return FeeStatus.PARTIALLY_PAID;
        }
        return FeeStatus.PENDING;
    }

    private FeeResponse toResponse(Fee fee, BigDecimal amountPaid) {
        return new FeeResponse(fee.getId(), fee.getStudentId(), fee.getAmountDue(), amountPaid,
                fee.getDueDate(), fee.getStatus());
    }
}
