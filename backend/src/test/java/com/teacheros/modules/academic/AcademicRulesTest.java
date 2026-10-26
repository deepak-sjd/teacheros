package com.teacheros.modules.academic;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.dto.AcademicDtos.PlaceStudentRequest;
import com.teacheros.modules.academic.dto.AcademicDtos.StudentPlacementResponse;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.tenant.TenantConfigService;
import com.teacheros.modules.tenant.TenantType;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantConfigResponse;
import com.teacheros.modules.user.Role;
import com.teacheros.modules.user.User;
import com.teacheros.modules.user.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcademicRulesTest {

    @Mock
    TenantConfigService tenantConfigService;
    @Mock
    UserRepository userRepository;
    @Mock
    AcademicStudentRepository studentRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    SchoolClassRepository classRepository;
    @Mock
    AcademicYearRepository yearRepository;
    @Mock
    CurrentTenantProvider tenantProvider;
    @Mock
    AuditService auditService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID yearId = UUID.randomUUID();

    private Student student;
    private Section section;
    private StudentPlacementService placement;
    private TeacherDirectory teachers;

    private static TenantConfigResponse config(TenantType type) {
        return new TenantConfigResponse(type, List.of(), "en-IN", "Asia/Kolkata", null, null);
    }

    @BeforeEach
    void setUp() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(tenantConfigService.get()).thenReturn(config(TenantType.SCHOOL));

        SchoolClass schoolClass = new SchoolClass(tenantId, "Class 8", 8);
        schoolClass.setId(classId);
        section = new Section(tenantId, classId, "A");
        section.setId(sectionId);
        AcademicYear year = new AcademicYear(tenantId, "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        year.setId(yearId);
        year.setCurrent(true);
        student = new Student(tenantId, null, "Asha", "8", "+919222222222");
        student.setId(studentId);

        when(studentRepository.findByIdAndTenantId(studentId, tenantId)).thenReturn(Optional.of(student));
        when(sectionRepository.findByIdAndTenantId(sectionId, tenantId)).thenReturn(Optional.of(section));
        when(classRepository.findByIdAndTenantId(classId, tenantId)).thenReturn(Optional.of(schoolClass));
        when(yearRepository.findByTenantIdAndCurrentTrue(tenantId)).thenReturn(Optional.of(year));
        when(yearRepository.findByIdAndTenantId(yearId, tenantId)).thenReturn(Optional.of(year));

        placement = new StudentPlacementService(studentRepository, sectionRepository, classRepository,
                yearRepository, new SchoolTenantGuard(tenantConfigService), tenantProvider, auditService);
        teachers = new TeacherDirectory(userRepository);
    }

    private User user(UUID id, UUID inTenant, Role role, boolean active) {
        User u = new User(inTenant, "+91900000" + (1000 + Math.abs(id.hashCode() % 8999)), "Someone", role);
        u.setId(id);
        u.setActive(active);
        return u;
    }

    @Test
    void coachingTenantsAreRejected() {
        when(tenantConfigService.get()).thenReturn(config(TenantType.COACHING));

        ApiException ex = assertThrows(ApiException.class,
                () -> placement.place(studentId, new PlaceStudentRequest(sectionId, "1", null)));

        assertEquals("SCHOOL_ONLY", ex.getCode());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void teacherMustBeAnActiveStaffMemberOfTheSameTenant() {
        UUID good = UUID.randomUUID(), otherTenant = UUID.randomUUID(), parent = UUID.randomUUID(),
                inactive = UUID.randomUUID(), missing = UUID.randomUUID();
        when(userRepository.findById(good)).thenReturn(Optional.of(user(good, tenantId, Role.TEACHER, true)));
        when(userRepository.findById(otherTenant))
                .thenReturn(Optional.of(user(otherTenant, UUID.randomUUID(), Role.TEACHER, true)));
        when(userRepository.findById(parent)).thenReturn(Optional.of(user(parent, tenantId, Role.PARENT, true)));
        when(userRepository.findById(inactive)).thenReturn(Optional.of(user(inactive, tenantId, Role.TEACHER, false)));
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertEquals(good, teachers.requireTeacher(tenantId, good).getId());
        for (UUID bad : List.of(otherTenant, parent, inactive, missing)) {
            ApiException ex = assertThrows(ApiException.class, () -> teachers.requireTeacher(tenantId, bad));
            assertEquals("INVALID_TEACHER", ex.getCode());
        }
    }

    @Test
    void placementWithoutAYearNeedsACurrentYear() {
        when(yearRepository.findByTenantIdAndCurrentTrue(tenantId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> placement.place(studentId, new PlaceStudentRequest(sectionId, "1", null)));

        assertEquals("NO_CURRENT_YEAR", ex.getCode());
    }

    @Test
    void placementUsesTheCurrentYearAndNormalisesTheRollNumber() {
        StudentPlacementResponse result = placement.place(studentId, new PlaceStudentRequest(sectionId, " a7 ", null));

        assertEquals(sectionId, student.getSectionId());
        assertEquals(yearId, student.getAcademicYearId());
        assertEquals("A7", student.getRollNumber());
        assertEquals("Class 8", result.className());
        verify(studentRepository).saveAndFlush(student);
        verify(auditService).record(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void fullSectionRejectsAnotherStudent() {
        section.setCapacity(30);
        when(studentRepository.countByTenantIdAndSectionIdAndActiveTrue(tenantId, sectionId)).thenReturn(30L);

        ApiException ex = assertThrows(ApiException.class,
                () -> placement.place(studentId, new PlaceStudentRequest(sectionId, "31", null)));

        assertEquals("SECTION_FULL", ex.getCode());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateRollNumberIsRejectedBeforeSaving() {
        when(studentRepository.existsByTenantIdAndSectionIdAndAcademicYearIdAndRollNumberAndActiveTrueAndIdNot(
                tenantId, sectionId, yearId, "7", studentId)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> placement.place(studentId, new PlaceStudentRequest(sectionId, "7", null)));

        assertEquals("ROLL_NUMBER_IN_USE", ex.getCode());
        verify(studentRepository, never()).saveAndFlush(any());
    }

    @Test
    void badRollNumberCharactersAreRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> placement.place(studentId, new PlaceStudentRequest(sectionId, "7; DROP", null)));

        assertEquals("INVALID_ROLL_NUMBER", ex.getCode());
    }

    @Test
    void repeatingTheSamePlacementChangesNothing() {
        student.setSectionId(sectionId);
        student.setAcademicYearId(yearId);
        student.setRollNumber("7");
        section.setCapacity(1); // full, but the student is already in it

        placement.place(studentId, new PlaceStudentRequest(sectionId, "7", null));

        verify(studentRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(anyString(), anyString(), any(), anyMap());
    }
}
