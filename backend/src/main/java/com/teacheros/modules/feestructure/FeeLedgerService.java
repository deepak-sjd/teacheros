package com.teacheros.modules.feestructure;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeLedgerLine;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.LedgerHeadLine;
import com.teacheros.modules.student.StudentAccessGuard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A student's fees with the discount broken out as its own figure per line. */
@Service
public class FeeLedgerService {

    private final FeeAssignmentRepository feeRepository;
    private final FeeLineItemRepository lineItemRepository;
    private final FeeHeadRepository headRepository;
    private final FeeStructureRepository structureRepository;
    private final FeePaymentReadRepository paymentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeLedgerService(FeeAssignmentRepository feeRepository, FeeLineItemRepository lineItemRepository,
            FeeHeadRepository headRepository, FeeStructureRepository structureRepository,
            FeePaymentReadRepository paymentRepository, StudentAccessGuard studentAccessGuard,
            CurrentTenantProvider currentTenantProvider) {
        this.feeRepository = feeRepository;
        this.lineItemRepository = lineItemRepository;
        this.headRepository = headRepository;
        this.structureRepository = structureRepository;
        this.paymentRepository = paymentRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<FeeLedgerLine> forStudent(UUID studentId) {
        studentAccessGuard.assertCanView(studentId);
        UUID tenantId = currentTenantProvider.getCurrentTenantId();

        List<Fee> fees = feeRepository.findByTenantIdAndStudentIdOrderByDueDateAsc(tenantId, studentId);
        if (fees.isEmpty()) {
            return List.of();
        }
        List<UUID> feeIds = fees.stream().map(Fee::getId).toList();
        Map<UUID, BigDecimal> paid = paymentRepository.sumPaidForFees(tenantId, feeIds).stream()
                .collect(Collectors.toMap(FeePaymentReadRepository.PaidTotalRow::getFeeId,
                        FeePaymentReadRepository.PaidTotalRow::getTotal));
        Map<UUID, List<FeeLineItem>> lines = lineItemRepository.findByTenantIdAndFeeIdIn(tenantId, feeIds).stream()
                .collect(Collectors.groupingBy(FeeLineItem::getFeeId));
        Map<UUID, String> headNames = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, FeeHead::getName));
        Map<UUID, String> structureNames = structureRepository.findByTenantIdOrderByAcademicYearDescNameAsc(tenantId)
                .stream().collect(Collectors.toMap(FeeStructure::getId, FeeStructure::getName, (a, b) -> a));

        return fees.stream().map(f -> {
            BigDecimal discount = f.getDiscountAmount() == null ? BigDecimal.ZERO : f.getDiscountAmount();
            BigDecimal gross = f.getGrossAmount() != null ? f.getGrossAmount() : f.getAmountDue().add(discount);
            String structure = f.getStructureId() == null ? null : structureNames.get(f.getStructureId());
            String description = switch (f.getLineType()) {
                case INSTALMENT -> (structure == null ? "Fee" : structure) + " - instalment " + f.getInstalmentNo();
                case LATE_FEE -> "Late fee";
                default -> "Fee";
            };
            List<LedgerHeadLine> heads = lines.getOrDefault(f.getId(), List.of()).stream()
                    .map(l -> new LedgerHeadLine(headNames.getOrDefault(l.getHeadId(), "Unknown"),
                            l.getGrossAmount(), l.getDiscountAmount()))
                    .toList();
            return new FeeLedgerLine(f.getId(), f.getLineType(), f.getInstalmentNo(), description, f.getDueDate(),
                    gross, discount, f.getAmountDue(), paid.getOrDefault(f.getId(), BigDecimal.ZERO), f.getStatus(), heads);
        }).toList();
    }
}
