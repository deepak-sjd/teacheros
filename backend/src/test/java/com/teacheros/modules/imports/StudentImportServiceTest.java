package com.teacheros.modules.imports;

import com.teacheros.common.exception.ApiException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.academic.AcademicStudentRepository;
import com.teacheros.modules.academic.AcademicStudentRepository.SectionCount;
import com.teacheros.modules.academic.AcademicYear;
import com.teacheros.modules.academic.AcademicYearRepository;
import com.teacheros.modules.academic.SchoolClass;
import com.teacheros.modules.academic.SchoolClassRepository;
import com.teacheros.modules.academic.Section;
import com.teacheros.modules.academic.SectionRepository;
import com.teacheros.modules.batch.Batch;
import com.teacheros.modules.batch.BatchRepository;
import com.teacheros.modules.imports.dto.ImportDtos.ImportResult;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentRepository;
import com.teacheros.modules.tenant.TenantConfigService;
import com.teacheros.modules.tenant.TenantType;
import com.teacheros.modules.tenant.dto.TenantDtos.TenantConfigResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentImportServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    BatchRepository batchRepository;
    @Mock
    AcademicStudentRepository academicStudents;
    @Mock
    SchoolClassRepository classRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    AcademicYearRepository yearRepository;
    @Mock
    TenantConfigService tenantConfigService;
    @Mock
    CurrentTenantProvider tenantProvider;
    @Captor
    ArgumentCaptor<Iterable<Student>> savedCaptor;

    private final UUID tenantId = UUID.randomUUID();
    private Batch morning;
    private StudentImportService service;

    private SchoolClass class8;
    private Section sectionA;
    private AcademicYear currentYear;

    private static TenantConfigResponse config(TenantType type) {
        return new TenantConfigResponse(type, List.of(), "en-IN", "Asia/Kolkata", null, null);
    }

    private StudentImportService newService(int maxRows) {
        return new StudentImportService(studentRepository, batchRepository, academicStudents, classRepository,
                sectionRepository, yearRepository, tenantConfigService, tenantProvider, maxRows);
    }

    @BeforeEach
    void setUp() {
        when(tenantProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(tenantConfigService.get()).thenReturn(config(TenantType.COACHING));
        morning = new Batch(tenantId, "Grade 10 - Morning", "Maths", null);
        morning.setId(UUID.randomUUID());
        when(batchRepository.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(morning));
        service = newService(2000);
    }

    /**
     * Switches the tenant to SCHOOL with one class (Class 8), one section (A) and a
     * current year.
     */
    private void schoolTenant() {
        when(tenantConfigService.get()).thenReturn(config(TenantType.SCHOOL));
        class8 = new SchoolClass(tenantId, "Class 8", 8);
        class8.setId(UUID.randomUUID());
        sectionA = new Section(tenantId, class8.getId(), "A");
        sectionA.setId(UUID.randomUUID());
        currentYear = new AcademicYear(tenantId, "2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31));
        currentYear.setId(UUID.randomUUID());
        currentYear.setCurrent(true);
        when(classRepository.findByTenantIdOrderByDisplayOrderAscNameAsc(tenantId)).thenReturn(List.of(class8));
        when(sectionRepository.findByTenantId(tenantId)).thenReturn(List.of(sectionA));
        when(yearRepository.findByTenantIdAndCurrentTrue(tenantId)).thenReturn(Optional.of(currentYear));
    }

    private static SectionCount count(UUID sectionId, long n) {
        return new SectionCount() {
            public UUID getSectionId() {
                return sectionId;
            }

            public long getStudentCount() {
                return n;
            }
        };
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "students.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private List<Student> savedStudents() {
        verify(studentRepository).saveAllAndFlush(savedCaptor.capture());
        List<Student> saved = new ArrayList<>();
        savedCaptor.getValue().forEach(saved::add);
        return saved;
    }

    // ---- Coaching behaviour (unchanged) ----

    @Test
    void validRowsAreSavedWithCallerTenantAndResolvedBatch() {
        ImportResult result = service.importStudents(csv(
                "fullName,grade,guardianPhone,phoneNumber,guardianEmail,batchName\n"
                        + "Asha Rao,10,9876543210,,asha@example.com,grade 10 - morning\n"),
                false, false);

        assertEquals(1, result.importedRows());
        assertEquals(0, result.errorRows());
        Student saved = savedStudents().get(0);
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(morning.getId(), saved.getBatchId());
        assertEquals("asha@example.com", saved.getGuardianEmail());
        assertNull(saved.getPhoneNumber());
    }

    @Test
    void dryRunValidatesButSavesNothing() {
        ImportResult result = service.importStudents(csv("fullName,guardianPhone\nAsha,9876543210\n"), true, false);

        assertTrue(result.dryRun());
        assertEquals(1, result.validRows());
        assertEquals(0, result.importedRows());
        verify(studentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void badRowsAreReportedWithTheirLineNumbers() {
        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone\n"
                        + "Asha Rao,9876543210\n"
                        + ",9876543211\n"
                        + "Ravi,12345\n"),
                true, false);

        assertEquals(2, result.errorRows());
        assertEquals(3, result.errors().get(0).lineNumber());
        assertTrue(result.errors().get(0).messages().get(0).contains("Full name is required"));
        assertEquals(4, result.errors().get(1).lineNumber());
        assertTrue(result.errors().get(1).messages().get(0).contains("Guardian phone must be"));
    }

    @Test
    void anyErrorBlocksTheWholeImportByDefault() {
        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone\nAsha,9876543210\nRavi,12345\n"), false, false);

        assertEquals(1, result.validRows());
        assertEquals(0, result.importedRows());
        verify(studentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void skipInvalidRowsImportsOnlyTheValidOnes() {
        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone\nAsha,9876543210\nRavi,12345\n"), false, true);

        assertEquals(1, result.importedRows());
        assertEquals(1, result.errorRows());
        assertEquals(1, savedStudents().size());
    }

    @Test
    void batchNameIsOnlyResolvedAgainstTheCallersOwnTenant() {
        UUID otherTenant = UUID.randomUUID();
        Batch foreign = new Batch(otherTenant, "Other School Batch", null, null);
        foreign.setId(UUID.randomUUID());
        when(batchRepository.findAllByTenantIdAndActiveTrue(otherTenant)).thenReturn(List.of(foreign));

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,batchName\nAsha,9876543210,Other School Batch\n"), false, false);

        assertEquals(1, result.errorRows());
        assertTrue(result.errors().get(0).messages().get(0).contains("was not found"));
        verify(batchRepository, never()).findAllByTenantIdAndActiveTrue(otherTenant);
        verify(studentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void duplicatesInTheFileAndInTheDatabaseAreFlagged() {
        when(studentRepository.findAllPhoneNumbersByTenantId(tenantId)).thenReturn(List.of("9222222222"));

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,phoneNumber\n"
                        + "Asha,9876543210,9111111111\n"
                        + "Ravi,9876543211,9111111111\n"
                        + "Asha,9876543210,\n"
                        + "Meena,9876543212,9222222222\n"),
                true, false);

        assertEquals(1, result.validRows());
        assertEquals(List.of(3, 4, 5), result.errors().stream().map(e -> e.lineNumber()).toList());
        assertTrue(result.errors().get(0).messages().get(0).contains("line 2"));
        assertTrue(result.errors().get(1).messages().get(0).contains("Duplicate of line 2"));
        assertTrue(result.errors().get(2).messages().get(0).contains("already used by another student"));
    }

    @Test
    void headerAliasesBomAndPhoneFormattingAreAccepted() {
        ImportResult result = service.importStudents(csv(
                "\uFEFFName,Class,Guardian Phone,Batch\nAsha,10,+91 98765 43210,Grade 10 - Morning\n"), false, false);

        assertEquals(1, result.importedRows());
        Student saved = savedStudents().get(0);
        assertEquals("+919876543210", saved.getGuardianPhone());
        assertEquals("10", saved.getGrade());
    }

    @Test
    void fiveHundredRowsImportQuickly() {
        StringBuilder sb = new StringBuilder("fullName,grade,guardianPhone\n");
        for (int i = 0; i < 500; i++) {
            sb.append("Student ").append(i).append(",9,").append(9000000000L + i).append('\n');
        }

        ImportResult result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> service.importStudents(csv(sb.toString()), false, false));

        assertEquals(500, result.totalRows());
        assertEquals(500, result.importedRows());
        assertEquals(500, savedStudents().size());
    }

    @Test
    void xlsxFilesAreReadAndNumericPhonesKeepTheirDigits() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Students");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("fullName");
            header.createCell(1).setCellValue("guardianPhone");
            Row good = sheet.createRow(1);
            good.createCell(0).setCellValue("Asha Rao");
            good.createCell(1).setCellValue(9876543210d); // typed as a number in Excel
            Row bad = sheet.createRow(2);
            bad.createCell(1).setCellValue(9876543211d); // missing name
            wb.write(out);
            bytes = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

        ImportResult result = service.importStudents(file, false, true);

        assertEquals(1, result.importedRows());
        assertEquals("9876543210", savedStudents().get(0).getGuardianPhone());
        assertEquals(3, result.errors().get(0).lineNumber()); // Excel row 3
    }

    @Test
    void missingRequiredColumnIsRejected() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.importStudents(csv("fullName,grade\nAsha,10\n"), true, false));
        assertEquals("MISSING_COLUMNS", ex.getCode());
    }

    @Test
    void unsupportedExtensionIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "students.xls", "application/vnd.ms-excel",
                new byte[] { 1 });
        ApiException ex = assertThrows(ApiException.class, () -> service.importStudents(file, true, false));
        assertEquals("UNSUPPORTED_IMPORT_FORMAT", ex.getCode());
    }

    @Test
    void moreRowsThanTheLimitIsRejected() {
        StudentImportService small = newService(2);
        ApiException ex = assertThrows(ApiException.class, () -> small.importStudents(
                csv("fullName,guardianPhone\nA,9876543210\nB,9876543211\nC,9876543212\n"), true, false));
        assertEquals("TOO_MANY_ROWS", ex.getCode());
    }

    // ---- COACHING tenants are unaffected by the school columns ----

    @Test
    void coachingTenantStillReadsClassAsGradeAndIgnoresSectionColumns() {
        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,Class,Section,Roll No\nAsha,9876543210,10,A,7\n"), false, false);

        assertEquals(1, result.importedRows());
        Student saved = savedStudents().get(0);
        assertEquals("10", saved.getGrade());
        assertNull(saved.getSectionId());
        assertNull(saved.getRollNumber());
        assertNull(saved.getAcademicYearId());
    }

    // ---- SCHOOL placement ----

    @Test
    void schoolImportPlacesStudentsInTheCurrentYearWithTheirRollNumber() {
        schoolTenant();

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,Class,Section,Roll No\nAsha,9876543210,8,A,07\n"), false, false);

        assertEquals(1, result.importedRows());
        Student saved = savedStudents().get(0);
        assertEquals(sectionA.getId(), saved.getSectionId());
        assertEquals(currentYear.getId(), saved.getAcademicYearId());
        assertEquals("07", saved.getRollNumber());
        assertNull(saved.getGrade()); // "Class" is the class name for a school, not the grade
    }

    @Test
    void classNamesMatchWithOrWithoutTheClassPrefix() {
        schoolTenant();

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName\n"
                        + "Asha,9876543210,Class 8,a\n"
                        + "Ravi,9876543211,grade8,A\n"
                        + "Meena,9876543212,8,A\n"),
                true, false);

        assertEquals(3, result.validRows());
        assertEquals(0, result.errorRows());
    }

    @Test
    void unknownClassOrSectionIsReportedPerRowWithLineNumbers() {
        schoolTenant();

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName\n"
                        + "Asha,9876543210,Class 9,A\n"
                        + "Ravi,9876543211,Class 8,Z\n"
                        + "Meena,9876543212,Class 8,A\n"),
                true, false);

        assertEquals(1, result.validRows());
        assertEquals(List.of(2, 3), result.errors().stream().map(e -> e.lineNumber()).toList());
        assertTrue(result.errors().get(0).messages().get(0).contains("Class \"Class 9\" was not found"));
        assertTrue(result.errors().get(1).messages().get(0).contains("has no section \"Z\""));
    }

    @Test
    void classWithoutSectionAndRollWithoutClassAreRejected() {
        schoolTenant();

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName,rollNumber\n"
                        + "Asha,9876543210,Class 8,,\n"
                        + "Ravi,9876543211,,A,\n"
                        + "Meena,9876543212,,,5\n"),
                true, false);

        assertEquals(3, result.errorRows());
        assertTrue(result.errors().get(0).messages().get(0).contains("Section is required"));
        assertTrue(result.errors().get(1).messages().get(0).contains("Class is required"));
        assertTrue(result.errors().get(2).messages().get(0).contains("needs a class and section"));
    }

    @Test
    void rollNumbersTakenInTheDatabaseOrEarlierInTheFileAreRejected() {
        schoolTenant();
        Student existing = new Student(tenantId, null, "Old", "8", "+919000000001");
        existing.setSectionId(sectionA.getId());
        existing.setRollNumber("7");
        when(academicStudents.findByTenantIdAndAcademicYearIdAndSectionIdInAndActiveTrue(
                eq(tenantId), eq(currentYear.getId()), anyCollection())).thenReturn(List.of(existing));

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName,rollNumber\n"
                        + "Asha,9876543210,8,A,7\n"
                        + "Ravi,9876543211,8,A,8\n"
                        + "Meena,9876543212,8,A,8\n"),
                true, false);

        assertEquals(1, result.validRows());
        assertEquals(List.of(2, 4), result.errors().stream().map(e -> e.lineNumber()).toList());
        assertTrue(result.errors().get(0).messages().get(0).contains("already taken"));
        assertTrue(result.errors().get(1).messages().get(0).contains("already used on line 3"));
    }

    @Test
    void sectionCapacityCountsExistingStudentsAndEarlierRows() {
        schoolTenant();
        sectionA.setCapacity(2);
        when(academicStudents.countActiveBySection(tenantId)).thenReturn(List.of(count(sectionA.getId(), 1)));

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName\n"
                        + "Asha,9876543210,8,A\n"
                        + "Ravi,9876543211,8,A\n"),
                true, false);

        assertEquals(1, result.validRows());
        assertEquals(3, result.errors().get(0).lineNumber());
        assertTrue(result.errors().get(0).messages().get(0).contains("is full (capacity 2)"));
    }

    @Test
    void placingStudentsNeedsACurrentYear() {
        schoolTenant();
        when(yearRepository.findByTenantIdAndCurrentTrue(tenantId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName\nAsha,9876543210,8,A\n"), false, false));

        assertEquals("NO_CURRENT_YEAR", ex.getCode());
        verify(studentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void aSchoolFileWithoutPlacementDoesNotNeedACurrentYear() {
        schoolTenant();
        when(yearRepository.findByTenantIdAndCurrentTrue(tenantId)).thenReturn(Optional.empty());

        ImportResult result = service.importStudents(csv("fullName,guardianPhone\nAsha,9876543210\n"), false, false);

        assertEquals(1, result.importedRows());
        assertNull(savedStudents().get(0).getSectionId());
    }

    @Test
    void schoolDryRunWithPlacementSavesNothing() {
        schoolTenant();

        ImportResult result = service.importStudents(csv(
                "fullName,guardianPhone,className,sectionName\nAsha,9876543210,8,A\n"), true, false);

        assertEquals(1, result.validRows());
        assertEquals(0, result.importedRows());
        verify(studentRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void fiveHundredSchoolRowsReportRowErrorsAndCreateNothingOnDryRun() {
        schoolTenant();
        StringBuilder sb = new StringBuilder("fullName,guardianPhone,className,sectionName,rollNumber\n");
        for (int i = 0; i < 500; i++) {
            sb.append("Student ").append(i).append(',').append(9000000000L + i).append(',')
                    .append(i % 50 == 0 ? "Class 99" : "Class 8").append(",A,").append(i + 1).append('\n');
        }

        ImportResult result = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> service.importStudents(csv(sb.toString()), true, false));

        assertEquals(500, result.totalRows());
        assertEquals(10, result.errorRows());
        assertEquals(490, result.validRows());
        assertEquals(0, result.importedRows());
        assertEquals(2, result.errors().get(0).lineNumber());
        verify(studentRepository, never()).saveAllAndFlush(any());
    }
}
