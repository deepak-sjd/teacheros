package com.teacheros.modules.fee;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
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
import java.util.List;
import java.util.UUID;

/**
 * Fee tracking only - no payment processing. A payment record here means
 * "the teacher told us cash/UPI/bank transfer was received," not that we
 * processed a transaction. See blueprint Section 27/28: real payment
 * collection is a distinct, later decision that needs a licensed provider.
 */
@Service
public class FeeService {

    private final FeeRepository feeRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final CurrentTenantProvider currentTenantProvider;
    private final StudentAccessGuard studentAccessGuard;

    public FeeService(FeeRepository feeRepository, FeePaymentRepository feePaymentRepository,
            CurrentTenantProvider currentTenantProvider, StudentAccessGuard studentAccessGuard) {
        this.feeRepository = feeRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.currentTenantProvider = currentTenantProvider;
        this.studentAccessGuard = studentAccessGuard;
    }

    @Transactional
    public FeeResponse createFee(CreateFeeRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = new Fee(tenantId, request.studentId(), request.amountDue(), request.dueDate());
        fee = feeRepository.save(fee);
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
        fee.setStatus(deriveStatus(fee.getAmountDue(), totalPaid));
        fee = feeRepository.save(fee);

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

    private FeeStatus deriveStatus(BigDecimal amountDue, BigDecimal totalPaid) {
        if (totalPaid.compareTo(amountDue) >= 0) {
            return FeeStatus.PAID;
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
