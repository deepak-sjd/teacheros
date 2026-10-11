package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.CreateFeeDiscountRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeDiscountResponse;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.UpdateFeeDiscountRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.WaiveLateFeeRequest;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-student discounts and late-fee waivers. Every change writes an audit
 * entry (FeeAuditWriter) in the same transaction. Endpoints require
 * FeePermissions.EDIT.
 *
 * A discount applies to fees generated AFTER it is created; it never rewrites
 * existing fees. A waiver is the one exception: it zeroes a late-fee line.
 */
@Service
public class FeeDiscountService {

    private final FeeDiscountRepository discountRepository;
    private final FeeHeadRepository headRepository;
    private final StudentRepository studentRepository;
    private final FeeAssignmentRepository feeRepository;
    private final FeePaymentReadRepository paymentRepository;
    private final FeeAuditWriter audit;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeDiscountService(FeeDiscountRepository discountRepository, FeeHeadRepository headRepository,
            StudentRepository studentRepository, FeeAssignmentRepository feeRepository,
            FeePaymentReadRepository paymentRepository, FeeAuditWriter audit,
            CurrentTenantProvider currentTenantProvider) {
        this.discountRepository = discountRepository;
        this.headRepository = headRepository;
        this.studentRepository = studentRepository;
        this.feeRepository = feeRepository;
        this.paymentRepository = paymentRepository;
        this.audit = audit;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<FeeDiscountResponse> list(UUID studentId) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<FeeDiscount> rows = studentId == null
                ? discountRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : discountRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId);
        Map<UUID, String> students = studentRepository.findAllByTenantIdAndActiveTrue(tenantId).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        Map<UUID, String> heads = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));
        return rows.stream().map(d -> toResponse(d, students, heads)).toList();
    }

    @Transactional
    public FeeDiscountResponse create(CreateFeeDiscountRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        validate(request.discountType(), request.value());
        Student student = studentRepository.findByIdAndTenantId(request.studentId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.studentId()));
        if (request.feeHeadId() != null) {
            headRepository.findByIdAndTenantId(request.feeHeadId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Fee head", request.feeHeadId()));
        }
        FeeDiscount d = discountRepository.save(new FeeDiscount(tenantId, student.getId(), request.feeHeadId(),
                request.discountType(), FeeCalculator.scale(request.value()), request.reason().trim(),
                currentTenantProvider.getCurrentUserId()));

        Map<String, Object> meta = new HashMap<>();
        meta.put("studentId", student.getId());
        meta.put("feeHeadId", request.feeHeadId());
        meta.put("type", d.getDiscountType());
        meta.put("value", d.getValue());
        meta.put("reason", d.getReason());
        audit.record("FEE_DISCOUNT_CREATED", "FEE_DISCOUNT", d.getId(), meta);
        return toResponse(d, Map.of(student.getId(), student.getFullName()), Map.of());
    }

    @Transactional
    public FeeDiscountResponse update(UUID id, UpdateFeeDiscountRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        validate(request.discountType(), request.value());
        FeeDiscount d = discountRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee discount", id));

        Map<String, Object> meta = new HashMap<>();
        meta.put("before", Map.of("type", d.getDiscountType(), "value", d.getValue(), "reason", d.getReason(),
                "active", d.isActive()));
        d.setDiscountType(request.discountType());
        d.setValue(FeeCalculator.scale(request.value()));
        d.setReason(request.reason().trim());
        d.setActive(request.active());
        d.setApprovedByUserId(currentTenantProvider.getCurrentUserId());
        discountRepository.save(d);
        meta.put("after", Map.of("type", d.getDiscountType(), "value", d.getValue(), "reason", d.getReason(),
                "active", d.isActive()));
        audit.record(request.active() ? "FEE_DISCOUNT_UPDATED" : "FEE_DISCOUNT_REVOKED", "FEE_DISCOUNT", d.getId(), meta);

        Map<UUID, String> students = studentRepository.findByIdAndTenantId(d.getStudentId(), tenantId)
                .map(s -> Map.of(s.getId(), s.getFullName())).orElse(Map.of());
        Map<UUID, String> heads = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));
        return toResponse(d, students, heads);
    }

    /**
     * Waives an unpaid late-fee line: the line stays (so the audit trail and
     * the once-per-instalment guard hold) but is fully discounted and PAID.
     */
    @Transactional
    public void waiveLateFee(WaiveLateFeeRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Fee fee = feeRepository.findByIdAndTenantId(request.feeId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee", request.feeId()));
        if (fee.getLineType() != FeeLineType.LATE_FEE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_LATE_FEE", "Only late-fee lines can be waived here.");
        }
        BigDecimal paid = paymentRepository.sumPaidForFees(tenantId, List.of(fee.getId())).stream()
                .map(FeePaymentReadRepository.PaidTotalRow::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.signum() > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LATE_FEE_HAS_PAYMENTS",
                    "This late fee already has a payment recorded and cannot be waived.");
        }
        if (fee.getAmountDue().signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALREADY_WAIVED", "This late fee is already waived.");
        }

        BigDecimal waived = fee.getAmountDue();
        fee.setGrossAmount(waived);
        fee.setDiscountAmount(waived);
        fee.setAmountDue(BigDecimal.ZERO.setScale(2));
        fee.setStatus(FeeStatus.PAID);
        feeRepository.save(fee);

        audit.record("LATE_FEE_WAIVED", "FEE", fee.getId(),
                Map.of("studentId", fee.getStudentId(), "waivedAmount", waived, "reason", request.reason().trim()));
    }

    private void validate(AmountType type, BigDecimal value) {
        if (type == AmountType.NONE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT_TYPE", "Choose FIXED or PERCENT.");
        }
        if (type == AmountType.PERCENT && value.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERCENT", "A percentage cannot exceed 100.");
        }
    }

    private FeeDiscountResponse toResponse(FeeDiscount d, Map<UUID, String> students, Map<UUID, String> heads) {
        return new FeeDiscountResponse(d.getId(), d.getStudentId(), students.getOrDefault(d.getStudentId(), "Unknown"),
                d.getFeeHeadId(), d.getFeeHeadId() == null ? "All heads" : heads.getOrDefault(d.getFeeHeadId(), "Unknown"),
                d.getDiscountType(), d.getValue(), d.getReason(), d.getApprovedByUserId(), d.isActive(), d.getCreatedAt());
    }
}
