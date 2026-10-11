package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.CreateFeeStructureRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.FeeStructureResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.StructureItemInput;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.StructureItemResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.UpdateFeeStructureRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeeStructureService {

    private final FeeStructureRepository structureRepository;
    private final FeeStructureItemRepository itemRepository;
    private final FeeHeadRepository headRepository;
    private final InstalmentPlanRepository planRepository;
    private final BatchRepository batchRepository;
    private final FeeAuditWriter audit;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeStructureService(FeeStructureRepository structureRepository, FeeStructureItemRepository itemRepository,
            FeeHeadRepository headRepository, InstalmentPlanRepository planRepository,
            BatchRepository batchRepository, FeeAuditWriter audit, CurrentTenantProvider currentTenantProvider) {
        this.structureRepository = structureRepository;
        this.itemRepository = itemRepository;
        this.headRepository = headRepository;
        this.planRepository = planRepository;
        this.batchRepository = batchRepository;
        this.audit = audit;
        this.currentTenantProvider = currentTenantProvider;
    }

    public List<FeeStructureResponse> list() {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        List<FeeStructure> structures = structureRepository.findByTenantIdOrderByAcademicYearDescNameAsc(tenantId);
        if (structures.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<FeeStructureItem>> items = itemRepository
                .findByTenantIdAndStructureIdIn(tenantId, structures.stream().map(FeeStructure::getId).toList())
                .stream().collect(Collectors.groupingBy(FeeStructureItem::getStructureId));
        Map<UUID, FeeHead> heads = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        Map<UUID, InstalmentPlan> plans = planRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(InstalmentPlan::getId, Function.identity()));
        return structures.stream()
                .map(s -> toResponse(s, items.getOrDefault(s.getId(), List.of()), heads, plans.get(s.getPlanId())))
                .toList();
    }

    @Transactional
    public FeeStructureResponse create(CreateFeeStructureRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        String name = request.name().trim();

        InstalmentPlan plan = planRepository.findByIdAndTenantId(request.planId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Instalment plan", request.planId()));
        if (request.batchId() != null) {
            batchRepository.findByIdAndTenantId(request.batchId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Batch", request.batchId()));
        }
        if (structureRepository.existsByTenantIdAndNameIgnoreCaseAndAcademicYear(tenantId, name, request.academicYear())) {
            throw new ApiException(HttpStatus.CONFLICT, "STRUCTURE_EXISTS",
                    "A structure named \"" + name + "\" already exists for " + request.academicYear() + ".");
        }

        Set<UUID> headIds = new HashSet<>();
        for (StructureItemInput item : request.items()) {
            if (!headIds.add(item.headId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_HEAD", "Each fee head can appear only once.");
            }
        }
        Map<UUID, FeeHead> heads = headRepository.findByTenantIdAndIdIn(tenantId, headIds).stream()
                .collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        for (UUID headId : headIds) {
            FeeHead head = heads.get(headId);
            if (head == null) {
                throw new ResourceNotFoundException("Fee head", headId);
            }
            if (!head.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HEAD_INACTIVE", "Fee head \"" + head.getName() + "\" is inactive.");
            }
        }

        String grade = request.grade() == null || request.grade().isBlank() ? null : request.grade().trim();
        FeeStructure structure = structureRepository.save(
                new FeeStructure(tenantId, name, request.academicYear(), grade, request.batchId(), plan.getId()));
        List<FeeStructureItem> items = itemRepository.saveAll(request.items().stream()
                .map(i -> new FeeStructureItem(tenantId, structure.getId(), i.headId(), FeeCalculator.scale(i.amount())))
                .toList());

        audit.record("FEE_STRUCTURE_CREATED", "FEE_STRUCTURE", structure.getId(),
                Map.of("name", name, "academicYear", request.academicYear(), "items", items.size()));
        return toResponse(structure, items, heads, plan);
    }

    @Transactional
    public FeeStructureResponse update(UUID id, UpdateFeeStructureRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        FeeStructure structure = structureRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee structure", id));
        structure.setName(request.name().trim());
        structure.setActive(request.active());
        structureRepository.save(structure);

        Map<UUID, FeeHead> heads = headRepository.findByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        InstalmentPlan plan = planRepository.findByIdAndTenantId(structure.getPlanId(), tenantId).orElse(null);
        return toResponse(structure, itemRepository.findByTenantIdAndStructureId(tenantId, id), heads, plan);
    }

    private FeeStructureResponse toResponse(FeeStructure s, List<FeeStructureItem> items, Map<UUID, FeeHead> heads,
            InstalmentPlan plan) {
        List<StructureItemResponse> rows = items.stream()
                .sorted(Comparator.comparingInt(i -> heads.containsKey(i.getHeadId()) ? heads.get(i.getHeadId()).getSortOrder() : 0))
                .map(i -> new StructureItemResponse(i.getHeadId(),
                        heads.containsKey(i.getHeadId()) ? heads.get(i.getHeadId()).getName() : "Unknown", i.getAmount()))
                .toList();
        BigDecimal total = items.stream().map(FeeStructureItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FeeStructureResponse(s.getId(), s.getName(), s.getAcademicYear(), s.getGrade(), s.getBatchId(),
                s.getPlanId(), plan == null ? null : plan.getName(), s.isActive(), rows, FeeCalculator.scale(total));
    }
}
