package com.teacheros.modules.feestructure;

import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.fee.Fee;
import com.teacheros.modules.fee.FeeLineType;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignStructureRequest;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentPreviewResponse;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentPreviewRow;
import com.teacheros.modules.feestructure.dto.FeeStructureDtos.AssignmentResultResponse;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeAssignmentServiceTest {

    @Mock
    FeeStructureRepository structureRepository;
    @Mock
    FeeStructureItemRepository itemRepository;
    @Mock
    FeeHeadRepository headRepository;
    @Mock
    InstalmentPlanItemRepository planItemRepository;
    @Mock
    StudentRepository studentRepository;
    @Mock
    FeeDiscountRepository discountRepository;
    @Mock
    FeeRulesRepository rulesRepository;
    @Mock
    FeeAssignmentRepository feeRepository;
    @Mock
    FeeLineItemRepository lineItemRepository;
    @Mock
    FeeAuditWriter audit;
    @Mock
    CurrentTenantProvider tenantProvider;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID structureId = UUID.randomUUID();
    private final UUID headId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private FeeAssignmentService service;
    private Student first;
    private Student second;

    private Student student(String name, String guardian, Instant createdAt) {
        Student s = new Student(tenantId, null, name, "8", guardian);
        s.setId(UUID.randomUUID());
        s.setCreatedAt(createdAt);
        return s;
    }

    @BeforeEach
    void setUp() {
        service = new FeeAssignmentService(structureRepository, itemRepository, headRepository, planItemRepository,
                studentRepository, discountRepository, rulesRepository, feeRepository, lineItemRepository, audit,
                tenantProvider);
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenantId);

        FeeStructure structure = new FeeStructure(tenantId, "Class 8 annual fee", "2026-27", "8", null, planId);
        structure.setId(structureId);
        when(structureRepository.findByIdAndTenantId(structureId, tenantId)).thenReturn(Optional.of(structure));

        FeeHead head = new FeeHead(tenantId, "Tuition", null, true, 0);
        head.setId(headId);
        when(headRepository.findByTenantIdAndIdIn(eq(tenantId), any())).thenReturn(List.of(head));
        when(itemRepository.findByTenantIdAndStructureId(tenantId, structureId))
                .thenReturn(List.of(new FeeStructureItem(tenantId, structureId, headId, new BigDecimal("40000.00"))));
        when(planItemRepository.findByTenantIdAndPlanIdOrderByInstalmentNoAsc(tenantId, planId)).thenReturn(List.of(
                new InstalmentPlanItem(tenantId, planId, 1, LocalDate.of(2026, 4, 10), new BigDecimal("25")),
                new InstalmentPlanItem(tenantId, planId, 2, LocalDate.of(2026, 7, 10), new BigDecimal("25")),
                new InstalmentPlanItem(tenantId, planId, 3, LocalDate.of(2026, 10, 10), new BigDecimal("25")),
                new InstalmentPlanItem(tenantId, planId, 4, LocalDate.of(2027, 1, 10), new BigDecimal("25"))));

        first = student("Asha", "+919000000001", Instant.parse("2026-01-01T00:00:00Z"));
        second = student("Bilal", "+919000000002", Instant.parse("2026-01-02T00:00:00Z"));
        when(studentRepository.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(first, second));
        when(rulesRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(discountRepository.findByTenantIdAndStudentIdInAndActiveTrue(eq(tenantId), any())).thenReturn(List.of());
        when(feeRepository.findByTenantIdAndStructureIdAndLineType(tenantId, structureId, FeeLineType.INSTALMENT))
                .thenReturn(List.of());
        when(feeRepository.saveAndFlush(any(Fee.class))).thenAnswer(inv -> {
            Fee f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(UUID.randomUUID());
            }
            return f;
        });
    }

    @Test
    void assigningCreatesFourFeesPerStudentWithCorrectAmountsAndDates() {
        AssignmentResultResponse result = service.assign(new AssignStructureRequest(structureId, null));

        assertThat(result.created()).isEqualTo(8);
        assertThat(result.skipped()).isZero();
        ArgumentCaptor<Fee> captor = ArgumentCaptor.forClass(Fee.class);
        verify(feeRepository, times(8)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(f -> {
            assertThat(f.getAmountDue()).isEqualByComparingTo("10000.00");
            assertThat(f.getLineType()).isEqualTo(FeeLineType.INSTALMENT);
            assertThat(f.getStructureId()).isEqualTo(structureId);
            assertThat(f.getFeeHeadId()).isEqualTo(headId);
        });
        assertThat(captor.getAllValues().stream().filter(f -> f.getStudentId().equals(first.getId()))
                .map(Fee::getDueDate).toList())
                .containsExactly(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 10, 10), LocalDate.of(2027, 1, 10));
    }

    @Test
    void assigningTwiceCreatesNothingTheSecondTime() {
        service.assign(new AssignStructureRequest(structureId, null));
        ArgumentCaptor<Fee> captor = ArgumentCaptor.forClass(Fee.class);
        verify(feeRepository, times(8)).saveAndFlush(captor.capture());

        // The database now "contains" what the first run saved.
        when(feeRepository.findByTenantIdAndStructureIdAndLineType(tenantId, structureId, FeeLineType.INSTALMENT))
                .thenReturn(new ArrayList<>(captor.getAllValues()));
        clearInvocations(feeRepository);

        AssignmentResultResponse again = service.assign(new AssignStructureRequest(structureId, null));

        assertThat(again.created()).isZero();
        assertThat(again.skipped()).isEqualTo(8);
        verify(feeRepository, never()).saveAndFlush(any(Fee.class));
    }

    @Test
    void secondChildOfTheSameGuardianGetsTheConcessionInThePreview() {
        second.setGuardianPhone(first.getGuardianPhone()); // siblings
        FeeRules rules = new FeeRules(tenantId);
        rules.setSiblingConcessionType(AmountType.PERCENT);
        rules.setSiblingConcessionValue(new BigDecimal("10.00"));
        when(rulesRepository.findById(tenantId)).thenReturn(Optional.of(rules));

        AssignmentPreviewResponse preview = service.preview(new AssignStructureRequest(structureId, null));

        List<AssignmentPreviewRow> firstRows = preview.rows().stream().filter(r -> r.studentId().equals(first.getId()))
                .toList();
        List<AssignmentPreviewRow> secondRows = preview.rows().stream()
                .filter(r -> r.studentId().equals(second.getId())).toList();
        assertThat(firstRows).allSatisfy(r -> assertThat(r.discountAmount()).isEqualByComparingTo("0.00"));
        assertThat(secondRows).allSatisfy(r -> {
            assertThat(r.discountAmount()).isEqualByComparingTo("1000.00");
            assertThat(r.amountDue()).isEqualByComparingTo("9000.00");
            assertThat(r.discountNotes()).contains("Sibling concession");
        });
        verify(feeRepository, never()).saveAndFlush(any(Fee.class)); // preview writes nothing
    }

    @Test
    void siblingRankIsByCreationOrderAcrossTheWholeTenant() {
        second.setGuardianPhone(first.getGuardianPhone());
        var ranks = FeeAssignmentService.siblingRanks(List.of(second, first));
        assertThat(ranks.get(first.getId())).isZero();
        assertThat(ranks.get(second.getId())).isEqualTo(1);
    }
}
