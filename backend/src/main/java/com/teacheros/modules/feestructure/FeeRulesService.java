package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeRulesRequest;
import com.teacheros.modules.feestructure.dto.FeeFinanceDtos.FeeRulesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FeeRulesService {

    private final FeeRulesRepository rulesRepository;
    private final FeeAuditWriter audit;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeRulesService(FeeRulesRepository rulesRepository, FeeAuditWriter audit,
            CurrentTenantProvider currentTenantProvider) {
        this.rulesRepository = rulesRepository;
        this.audit = audit;
        this.currentTenantProvider = currentTenantProvider;
    }

    public FeeRulesResponse get() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        return toResponse(rulesRepository.findById(tenantId).orElseGet(() -> new FeeRules(tenantId)));
    }

    @Transactional
    public FeeRulesResponse update(FeeRulesRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        check(request.siblingConcessionType(), request.siblingConcessionValue(), "sibling concession");
        check(request.lateFeeType(), request.lateFeeValue(), "late fee");

        FeeRules rules = rulesRepository.findById(tenantId).orElseGet(() -> new FeeRules(tenantId));
        Map<String, Object> meta = new HashMap<>();
        meta.put("before", snapshot(rules));
        rules.setSiblingConcessionType(request.siblingConcessionType());
        rules.setSiblingConcessionValue(FeeCalculator.scale(request.siblingConcessionValue()));
        rules.setLateFeeType(request.lateFeeType());
        rules.setLateFeeValue(FeeCalculator.scale(request.lateFeeValue()));
        rules.setLateFeeGraceDays(request.lateFeeGraceDays());
        rules.setUpdatedByUserId(currentTenantProvider.getCurrentUserId());
        rules = rulesRepository.save(rules);
        meta.put("after", snapshot(rules));
        audit.record("FEE_RULES_UPDATED", "FEE_RULES", tenantId, meta);
        return toResponse(rules);
    }

    private void check(AmountType type, BigDecimal value, String what) {
        if (type == AmountType.PERCENT && value.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PERCENT", "The " + what + " percentage cannot exceed 100.");
        }
        if (type != AmountType.NONE && value.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VALUE", "Enter a value above 0 for the " + what + ", or choose NONE.");
        }
    }

    private Map<String, Object> snapshot(FeeRules r) {
        return Map.of("siblingType", r.getSiblingConcessionType(), "siblingValue", r.getSiblingConcessionValue(),
                "lateFeeType", r.getLateFeeType(), "lateFeeValue", r.getLateFeeValue(),
                "graceDays", r.getLateFeeGraceDays());
    }

    private FeeRulesResponse toResponse(FeeRules r) {
        return new FeeRulesResponse(r.getSiblingConcessionType(), r.getSiblingConcessionValue(), r.getLateFeeType(),
                r.getLateFeeValue(), r.getLateFeeGraceDays(), r.getUpdatedAt());
    }
}
