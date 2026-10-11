package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateInstalmentPlanRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.InstalmentInput;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.InstalmentPlanResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.InstalmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InstalmentPlanService {

    private final InstalmentPlanRepository planRepository;
    private final InstalmentPlanItemRepository itemRepository;
    private final CurrentTenantProvider currentTenantProvider;

    public InstalmentPlanService(InstalmentPlanRepository planRepository, InstalmentPlanItemRepository itemRepository,
            CurrentTenantProvider currentTenantProvider) {
        this.planRepository = planRepository;
        this.itemRepository = itemRepository;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<InstalmentPlanResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<InstalmentPlan> plans = planRepository.findByTenantIdOrderByNameAsc(tenantId);
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<InstalmentPlanItem>> items = itemRepository
                .findByTenantIdAndPlanIdInOrderByInstalmentNoAsc(tenantId, plans.stream().map(InstalmentPlan::getId).toList())
                .stream().collect(Collectors.groupingBy(InstalmentPlanItem::getPlanId));
        return plans.stream().map(p -> toResponse(p, items.getOrDefault(p.getId(), List.of()))).toList();
    }

    @Transactional
    public InstalmentPlanResponse create(CreateInstalmentPlanRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String name = request.name().trim();
        if (planRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_EXISTS", "An instalment plan named \"" + name + "\" already exists.");
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < request.instalments().size(); i++) {
            InstalmentInput in = request.instalments().get(i);
            sum = sum.add(in.percentage());
            if (i > 0 && !in.dueDate().isAfter(request.instalments().get(i - 1).dueDate())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_DATES_NOT_ASCENDING",
                        "Instalment due dates must be in increasing order.");
            }
        }
        if (sum.compareTo(new BigDecimal("100")) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_PERCENTAGES_INVALID",
                    "Instalment percentages must add up to exactly 100 (currently " + sum.toPlainString() + ").");
        }

        InstalmentPlan plan = planRepository.save(new InstalmentPlan(tenantId, name, request.instalments().size()));
        List<InstalmentPlanItem> saved = new ArrayList<>();
        for (int i = 0; i < request.instalments().size(); i++) {
            InstalmentInput in = request.instalments().get(i);
            saved.add(new InstalmentPlanItem(tenantId, plan.getId(), i + 1, in.dueDate(), in.percentage()));
        }
        return toResponse(plan, itemRepository.saveAll(saved));
    }

    private InstalmentPlanResponse toResponse(InstalmentPlan p, List<InstalmentPlanItem> items) {
        return new InstalmentPlanResponse(p.getId(), p.getName(), p.getNumberOfInstalments(), p.isActive(),
                items.stream().map(i -> new InstalmentResponse(i.getInstalmentNo(), i.getDueDate(), i.getPercentage())).toList());
    }
}
