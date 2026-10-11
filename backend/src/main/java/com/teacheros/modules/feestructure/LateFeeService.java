package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds a late fee as its own fee row for each overdue structure instalment.
 *
 * Takes the tenant id explicitly (no CurrentTenantProvider) because the
 * scheduler runs without a security context. Only structure-generated
 * INSTALMENT fees are charged: legacy flat fees are never touched.
 *
 * Never double-charges: candidates exclude instalments that already have a
 * LATE_FEE child, and uq_fees_late_fee_parent rejects a second one even if
 * two runs race. A percent late fee is a percent of the instalment's net
 * amount_due.
 */
@Service
public class LateFeeService {

    private final FeeRulesRepository rulesRepository;
    private final FeeAssignmentRepository feeRepository;
    private final FeeAuditWriter audit;

    public LateFeeService(FeeRulesRepository rulesRepository, FeeAssignmentRepository feeRepository,
            FeeAuditWriter audit) {
        this.rulesRepository = rulesRepository;
        this.feeRepository = feeRepository;
        this.audit = audit;
    }

    @Transactional
    public int applyForTenant(UUID tenantId, LocalDate today, UUID actorUserId) {
        FeeRules rules = rulesRepository.findById(tenantId).orElse(null);
        if (rules == null || rules.getLateFeeType() == AmountType.NONE || rules.getLateFeeValue().signum() <= 0) {
            return 0;
        }
        LocalDate cutoff = today.minusDays(rules.getLateFeeGraceDays());
        List<Fee> candidates = feeRepository.findLateFeeCandidates(tenantId, FeeLineType.INSTALMENT,
                FeeLineType.LATE_FEE, FeeStatus.PAID, cutoff);

        int created = 0;
        for (Fee parent : candidates) {
            BigDecimal amount = rules.getLateFeeType() == AmountType.FIXED
                    ? FeeCalculator.scale(rules.getLateFeeValue())
                    : FeeCalculator.scale(parent.getAmountDue().multiply(rules.getLateFeeValue())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            if (amount.signum() <= 0) {
                continue;
            }
            Fee late = new Fee(tenantId, parent.getStudentId(), amount, today);
            late.setLineType(FeeLineType.LATE_FEE);
            late.setParentFeeId(parent.getId());
            late.setStructureId(parent.getStructureId());
            late.setGrossAmount(amount);
            try {
                feeRepository.saveAndFlush(late);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(HttpStatus.CONFLICT, "LATE_FEE_RUN_CONFLICT",
                        "A late-fee run is already in progress. Try again in a moment.");
            }
            created++;
        }

        if (created > 0) {
            audit.recordFor(tenantId, actorUserId, "LATE_FEES_APPLIED", "FEE_RULES", tenantId,
                    Map.of("created", created, "asOf", today.toString()));
        }
        return created;
    }
}
