package com.teacheros.modules.feestructure;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.fee.FeeStatus;
import com.teacheros.modules.feestructure.FeeCalculator.DiscountSpec;
import com.teacheros.modules.feestructure.FeeCalculator.HeadInput;
import com.teacheros.modules.feestructure.FeeCalculator.InstalmentResult;
import com.teacheros.modules.feestructure.FeeCalculator.InstalmentSpec;
import com.teacheros.modules.feestructure.FeeCalculator.LineResult;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignStructureRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentPreviewResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentPreviewRow;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentResultResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a fee structure into ordinary fees rows, one per student per
 * instalment. preview() and assign() share buildPlan(), so what the
 * accountant sees in the preview is exactly what assign() writes.
 *
 * Idempotent: an instalment that already exists for (student, structure,
 * instalment_no) is skipped, and uq_fees_structure_instalment backs that up
 * at the database level.
 *
 * Money handling note for Dev 3: this only creates fees (amount_due is the
 * net amount, status PENDING, or PAID when a 100% discount makes it zero).
 * It never touches fee_payments.
 */
@Service
public class FeeAssignmentService {

    private final FeeStructureRepository structureRepository;
    private final FeeStructureItemRepository itemRepository;
    private final FeeHeadRepository headRepository;
    private final InstalmentPlanItemRepository planItemRepository;
    private final StudentRepository studentRepository;
    private final FeeDiscountRepository discountRepository;
    private final FeeRulesRepository rulesRepository;
    private final FeeAssignmentRepository feeRepository;
    private final FeeLineItemRepository lineItemRepository;
    private final FeeAuditWriter audit;
    private final CurrentTenantProvider currentTenantProvider;

    public FeeAssignmentService(FeeStructureRepository structureRepository, FeeStructureItemRepository itemRepository,
            FeeHeadRepository headRepository, InstalmentPlanItemRepository planItemRepository,
            StudentRepository studentRepository, FeeDiscountRepository discountRepository,
            FeeRulesRepository rulesRepository, FeeAssignmentRepository feeRepository,
            FeeLineItemRepository lineItemRepository, FeeAuditWriter audit,
            CurrentTenantProvider currentTenantProvider) {
        this.structureRepository = structureRepository;
        this.itemRepository = itemRepository;
        this.headRepository = headRepository;
        this.planItemRepository = planItemRepository;
        this.studentRepository = studentRepository;
        this.discountRepository = discountRepository;
        this.rulesRepository = rulesRepository;
        this.feeRepository = feeRepository;
        this.lineItemRepository = lineItemRepository;
        this.audit = audit;
        this.currentTenantProvider = currentTenantProvider;
    }

    private record PlanRow(Student student, InstalmentResult instalment, String notes, boolean existing) {
    }

    private record BuiltPlan(FeeStructure structure, int headCount, UUID singleHeadId, int studentCount,
            List<PlanRow> rows) {
    }

    public AssignmentPreviewResponse preview(AssignStructureRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        BuiltPlan plan = buildPlan(tenantId, request);

        int toCreate = 0;
        int existing = 0;
        BigDecimal charge = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        List<AssignmentPreviewRow> rows = new ArrayList<>();
        for (PlanRow r : plan.rows()) {
            if (r.existing()) {
                existing++;
            } else {
                toCreate++;
                charge = charge.add(r.instalment().net());
                discount = discount.add(r.instalment().discount());
            }
            rows.add(new AssignmentPreviewRow(r.student().getId(), r.student().getFullName(), r.student().getGrade(),
                    r.instalment().no(), r.instalment().dueDate(), r.instalment().gross(), r.instalment().discount(),
                    r.instalment().net(), r.notes(), r.existing()));
        }
        return new AssignmentPreviewResponse(plan.structure().getId(), plan.structure().getName(),
                plan.studentCount(), toCreate, existing, charge, discount, rows);
    }

    @Transactional
    public AssignmentResultResponse assign(AssignStructureRequest request) {
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        BuiltPlan plan = buildPlan(tenantId, request);

        int created = 0;
        int skipped = 0;
        BigDecimal charged = BigDecimal.ZERO;
        for (PlanRow r : plan.rows()) {
            if (r.existing()) {
                skipped++;
                continue;
            }
            InstalmentResult res = r.instalment();
            Fee fee = new Fee(tenantId, r.student().getId(), res.net(), res.dueDate());
            fee.setLineType(FeeLineType.INSTALMENT);
            fee.setStructureId(plan.structure().getId());
            fee.setInstalmentNo(res.no());
            fee.setGrossAmount(res.gross());
            fee.setDiscountAmount(res.discount());
            fee.setFeeHeadId(plan.singleHeadId());
            if (res.net().signum() == 0) {
                fee.setStatus(FeeStatus.PAID); // fully discounted: nothing to collect
            }
            try {
                fee = feeRepository.saveAndFlush(fee);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(HttpStatus.CONFLICT, "ASSIGNMENT_CONFLICT",
                        "Another assignment of this structure is running. Refresh the preview and try again.");
            }
            for (LineResult line : res.lines()) {
                lineItemRepository.save(new FeeLineItem(tenantId, fee.getId(), line.headId(), line.gross(), line.discount()));
            }
            created++;
            charged = charged.add(res.net());
        }

        audit.record("FEE_STRUCTURE_ASSIGNED", "FEE_STRUCTURE", plan.structure().getId(), Map.of(
                "structure", plan.structure().getName(), "students", plan.studentCount(),
                "created", created, "skipped", skipped, "totalCharged", charged));
        return new AssignmentResultResponse(plan.studentCount(), created, skipped, charged);
    }

    private BuiltPlan buildPlan(UUID tenantId, AssignStructureRequest request) {
        FeeStructure structure = structureRepository.findByIdAndTenantId(request.structureId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee structure", request.structureId()));
        if (!structure.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STRUCTURE_INACTIVE", "This fee structure is inactive.");
        }

        List<FeeStructureItem> items = itemRepository.findByTenantIdAndStructureId(tenantId, structure.getId());
        Map<UUID, FeeHead> heads = headRepository
                .findByTenantIdAndIdIn(tenantId, items.stream().map(FeeStructureItem::getHeadId).toList()).stream()
                .collect(Collectors.toMap(FeeHead::getId, Function.identity()));
        List<HeadInput> headInputs = items.stream()
                .filter(i -> heads.containsKey(i.getHeadId()))
                .sorted(Comparator.comparingInt((FeeStructureItem i) -> heads.get(i.getHeadId()).getSortOrder())
                        .thenComparing(i -> heads.get(i.getHeadId()).getName()))
                .map(i -> new HeadInput(i.getHeadId(), i.getAmount(), heads.get(i.getHeadId()).isSiblingConcessionEligible()))
                .toList();

        List<InstalmentSpec> instalments = planItemRepository
                .findByTenantIdAndPlanIdOrderByInstalmentNoAsc(tenantId, structure.getPlanId()).stream()
                .map(p -> new InstalmentSpec(p.getInstalmentNo(), p.getDueDate(), p.getPercentage()))
                .toList();
        if (headInputs.isEmpty() || instalments.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STRUCTURE_INCOMPLETE",
                    "This structure has no fee heads or no instalments.");
        }

        List<Student> allActive = studentRepository.findAllByTenantIdAndActiveTrue(tenantId);
        List<Student> targets = resolveTargets(structure, request.studentIds(), allActive);

        FeeRules rules = rulesRepository.findById(tenantId).orElse(null);
        Map<UUID, Integer> siblingRank = siblingRanks(allActive);

        Map<UUID, List<FeeDiscount>> manual = targets.isEmpty() ? Map.of()
                : discountRepository.findByTenantIdAndStudentIdInAndActiveTrue(tenantId,
                        targets.stream().map(Student::getId).toList()).stream()
                        .collect(Collectors.groupingBy(FeeDiscount::getStudentId));

        Set<String> existingKeys = feeRepository
                .findByTenantIdAndStructureIdAndLineType(tenantId, structure.getId(), FeeLineType.INSTALMENT).stream()
                .map(f -> f.getStudentId() + ":" + f.getInstalmentNo())
                .collect(Collectors.toCollection(HashSet::new));

        List<PlanRow> rows = new ArrayList<>();
        for (Student student : targets) {
            List<DiscountSpec> specs = new ArrayList<>();
            for (FeeDiscount d : manual.getOrDefault(student.getId(), List.of())) {
                specs.add(new DiscountSpec(d.getFeeHeadId(), d.getDiscountType(), d.getValue(),
                        "Discount: " + d.getReason(), false));
            }
            if (rules != null && rules.getSiblingConcessionType() != AmountType.NONE
                    && rules.getSiblingConcessionValue().signum() > 0
                    && siblingRank.getOrDefault(student.getId(), 0) >= 1) {
                specs.add(new DiscountSpec(null, rules.getSiblingConcessionType(), rules.getSiblingConcessionValue(),
                        "Sibling concession", true));
            }
            FeeCalculator.Result result = FeeCalculator.calculate(headInputs, specs, instalments);
            String notes = String.join("; ", result.discountLabels());
            for (InstalmentResult ir : result.instalments()) {
                rows.add(new PlanRow(student, ir, notes, existingKeys.contains(student.getId() + ":" + ir.no())));
            }
        }
        return new BuiltPlan(structure, headInputs.size(), headInputs.size() == 1 ? headInputs.get(0).headId() : null,
                targets.size(), rows);
    }

    private List<Student> resolveTargets(FeeStructure structure, List<UUID> studentIds, List<Student> allActive) {
        List<Student> targets;
        if (studentIds != null && !studentIds.isEmpty()) {
            Set<UUID> wanted = new HashSet<>(studentIds);
            targets = allActive.stream().filter(s -> wanted.contains(s.getId())).collect(Collectors.toList());
            if (targets.size() != wanted.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "STUDENT_NOT_FOUND",
                        "One or more selected students were not found or are inactive.");
            }
        } else {
            if (structure.getBatchId() == null && structure.getGrade() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED",
                        "This structure has no batch or grade, so choose the students to assign it to.");
            }
            targets = allActive.stream()
                    .filter(s -> structure.getBatchId() == null || structure.getBatchId().equals(s.getBatchId()))
                    .filter(s -> structure.getGrade() == null
                            || (s.getGrade() != null && s.getGrade().trim().equalsIgnoreCase(structure.getGrade())))
                    .collect(Collectors.toList());
        }
        targets.sort(Comparator.comparing(Student::getFullName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    /**
     * 0 for the first-created child of a guardian_phone, 1 for the second,
     * and so on. Ranked across ALL active students in the tenant (not just
     * the ones being assigned), so assigning one class does not make a
     * younger sibling look like a first child.
     */
    static Map<UUID, Integer> siblingRanks(List<Student> allActive) {
        Map<UUID, Integer> rank = new HashMap<>();
        Comparator<Student> order = Comparator
                .comparing(Student::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Student::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        allActive.stream()
                .filter(s -> s.getGuardianPhone() != null && !s.getGuardianPhone().isBlank())
                .collect(Collectors.groupingBy(Student::getGuardianPhone))
                .values()
                .forEach(group -> {
                    group.sort(order);
                    for (int i = 0; i < group.size(); i++) {
                        rank.put(group.get(i).getId(), i);
                    }
                });
        return rank;
    }
}
