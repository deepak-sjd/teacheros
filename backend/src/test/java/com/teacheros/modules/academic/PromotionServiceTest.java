package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.dto.AcademicDtos.AcademicYearResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionPreviewResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionRequest;
import com.teacheros.modules.academic.dto.PromotionDtos.PromotionResultResponse;
import com.teacheros.modules.academic.dto.PromotionDtos.StudentActionOverride;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.TenantConfigService;
import com.teacheros.modules.tenant.TenantType;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromotionServiceTest {

    @Mock
    AcademicStudentRepository studentRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    SchoolClassRepository classRepository;
    @Mock
    AcademicYearRepository yearRepository;
    @Mock
    AcademicYearService yearService;
    @Mock
    TenantConfigService tenantConfigService;
    @Mock
    CurrentTenantProvider tenantProvider;
    @Mock
    AuditService auditService;

    private final UUID tenantId = UUID.randomUUID();
    private AcademicYear from;
    private SchoolClass c8, c9;
    private Section a8, b8, a9;
    private PromotionService service;

    @BeforeEach
    void setUp() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(tenantConfigService.get()).thenReturn(
                new TenantConfigResponse(TenantType.SCHOOL, List.of(), "en-IN", "Asia/Kolkata", null, null));

        from = year("2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        c8 = cls("Class 8", 8);
        c9 = cls("Class 9", 9);
        a8 = sec(c8, "A");
        b8 = sec(c8, "B");
        a9 = sec(c9, "A");

        when(yearRepository.findByIdAndTenantId(from.getId(), tenantId)).thenReturn(Optional.of(from));
        when(yearRepository.findByTenantIdOrderByStartDateDesc(tenantId)).thenReturn(List.of(from));
        when(sectionRepository.findByTenantId(tenantId)).thenReturn(List.of(a8, b8, a9));
        when(classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId)).thenReturn(List.of(c8, c9));

        service = new PromotionService(studentRepository, sectionRepository, classRepository, yearRepository,
                yearService, new SchoolTenantGuard(tenantConfigService), tenantProvider, auditService);
    }

    private AcademicYear year(String name, LocalDate start, LocalDate end) {
        AcademicYear y = new AcademicYear(tenantId, name, start, end);
        y.setId(UUID.randomUUID());
        return y;
    }

    private SchoolClass cls(String name, int order) {
        SchoolClass c = new SchoolClass(tenantId, name, order);
        c.setId(UUID.randomUUID());
        return c;
    }

    private Section sec(SchoolClass c, String name) {
        Section s = new Section(tenantId, c.getId(), name);
        s.setId(UUID.randomUUID());
        return s;
    }

    private Student student(String name, Section section) {
        Student s = new Student(tenantId, null, name, "8", "+919222222222");
        s.setId(UUID.randomUUID());
        s.setSectionId(section.getId());
        s.setAcademicYearId(from.getId());
        s.setRollNumber("5");
        return s;
    }

    private void studentsInFromYear(Student... students) {
        when(studentRepository.findByTenantIdAndAcademicYearIdAndSectionIdInAndActiveTrue(
                any(), any(), anyCollection())).thenReturn(List.of(students));
    }

    private PromotionRequest request() {
        return new PromotionRequest(from.getId(), null, null, null, null, null);
    }

    private AcademicYear stubNextYearCreation() {
        AcademicYear next = year("2027-28", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31));
        when(yearService.create(any())).thenReturn(
                new AcademicYearResponse(next.getId(), next.getName(), next.getStartDate(), next.getEndDate(), false));
        when(yearRepository.findByIdAndTenantId(next.getId(), tenantId)).thenReturn(Optional.of(next));
        return next;
    }

    @Test
    void previewListsEveryStudentAndWritesNothing() {
        studentsInFromYear(student("Asha", a8), student("Bina", a8));

        PromotionPreviewResponse preview = service.preview(request());

        assertEquals(2, preview.rows().size());
        assertEquals(2, preview.promoteCount());
        assertEquals("2027-28", preview.toAcademicYearName());
        assertTrue(preview.toYearWillBeCreated());
        verify(yearService, never()).create(any());
        verify(studentRepository, never()).saveAll(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void applyMovesStudentsIntoANewlyCreatedYearAndClearsRollNumbers() {
        Student asha = student("Asha", a8);
        studentsInFromYear(asha);
        AcademicYear next = stubNextYearCreation();

        PromotionResultResponse result = service.apply(request());

        assertTrue(result.createdYear());
        assertEquals(1, result.promoted());
        assertEquals(a9.getId(), asha.getSectionId());
        assertEquals(next.getId(), asha.getAcademicYearId());
        assertNull(asha.getRollNumber());
        verify(yearService).setCurrent(next.getId());
        verify(studentRepository).saveAll(any());
        verify(auditService).record(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void applyCanLeaveTheNewYearNonCurrent() {
        studentsInFromYear(student("Asha", a8));
        stubNextYearCreation();

        service.apply(new PromotionRequest(from.getId(), null, null, null, null, false));

        verify(yearService, never()).setCurrent(any());
    }

    @Test
    void applyDetainsAndDeactivatesAsRequested() {
        Student detained = student("Asha", a8);
        Student leaver = student("Ravi", a8);
        studentsInFromYear(detained, leaver);
        AcademicYear next = stubNextYearCreation();

        PromotionResultResponse result = service.apply(new PromotionRequest(from.getId(), null, null, null,
                List.of(new StudentActionOverride(detained.getId(), PromotionAction.DETAIN),
                        new StudentActionOverride(leaver.getId(), PromotionAction.LEAVE)),
                null));

        assertEquals(1, result.detained());
        assertEquals(1, result.left());
        assertEquals(a8.getId(), detained.getSectionId());
        assertEquals(next.getId(), detained.getAcademicYearId());
        assertFalse(leaver.isActive());
    }

    @Test
    void applyRefusesWhileAnyStudentHasNoTargetSection() {
        studentsInFromYear(student("Ravi", b8)); // Class 9 has no section B

        ApiException ex = assertThrows(ApiException.class, () -> service.apply(request()));

        assertEquals("PROMOTION_BLOCKED", ex.getCode());
        verify(yearService, never()).create(any());
        verify(studentRepository, never()).saveAll(any());
    }

    @Test
    void applyingAgainChangesNothing() {
        studentsInFromYear(); // everyone already moved out of the from-year

        PromotionResultResponse result = service.apply(request());

        assertEquals(0, result.promoted() + result.detained() + result.left());
        assertFalse(result.createdYear());
        verify(yearService, never()).create(any());
        verify(yearService, never()).setCurrent(any());
        verify(studentRepository, never()).saveAll(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void anOverrideForAStudentOutsideThePromotionIsRejected() {
        studentsInFromYear(student("Asha", a8));

        ApiException ex = assertThrows(ApiException.class, () -> service.preview(new PromotionRequest(
                from.getId(), null, null, null,
                List.of(new StudentActionOverride(UUID.randomUUID(), PromotionAction.LEAVE)), null)));

        assertEquals("UNKNOWN_STUDENT", ex.getCode());
    }
}
