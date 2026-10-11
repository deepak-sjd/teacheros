package com.teacheros.modules.permission;

import com.teacheros.common.security.JwtAuthenticationFilter;
import com.teacheros.common.security.JwtService;
import com.teacheros.config.SecurityConfig;
import com.teacheros.modules.assessment.AssessmentController;
import com.teacheros.modules.assessment.AssessmentService;
import com.teacheros.modules.batch.BatchController;
import com.teacheros.modules.batch.BatchService;
import com.teacheros.modules.fee.FeeController;
import com.teacheros.modules.fee.FeeService;
import com.teacheros.modules.homework.HomeworkController;
import com.teacheros.modules.homework.HomeworkService;
import com.teacheros.modules.report.ReportController;
import com.teacheros.modules.report.ReportPdfService;
import com.teacheros.modules.report.ReportService;
import com.teacheros.modules.student.StudentController;
import com.teacheros.modules.student.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "perm" is mocked (default: deny), so this proves the annotation wiring and
 * that the legacy role lists still reject ACCOUNTANT. The real DB-backed
 * defaults are covered by RolePermissionRepositoryTest.
 */
@WebMvcTest(controllers = {
        FeeController.class, ReportController.class, HomeworkController.class,
        StudentController.class, BatchController.class, AssessmentController.class })
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
class PermissionAnnotationWebTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean(name = "perm")
    PermissionService perm;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    FeeService feeService;
    @MockitoBean
    ReportService reportService;
    @MockitoBean
    ReportPdfService reportPdfService;
    @MockitoBean
    HomeworkService homeworkService;
    @MockitoBean
    StudentService studentService;
    @MockitoBean
    BatchService batchService;
    @MockitoBean
    AssessmentService assessmentService;

    private static final String ID = UUID.randomUUID().toString();

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantWithFeesViewCanListPendingFees() throws Exception {
        when(perm.has("FEES_VIEW")).thenReturn(true);
        when(feeService.listPending(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/fees/pending")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantWithFeesEditCanCreateFee() throws Exception {
        when(perm.has("FEES_EDIT")).thenReturn(true);

        mvc.perform(post("/api/v1/fees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + ID + "\",\"amountDue\":100,\"dueDate\":\"2026-01-01\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "COUNSELLOR")
    void callerWithoutFeesPermissionIsDeniedFees() throws Exception {
        mvc.perform(get("/api/v1/fees/pending")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + ID + "\",\"amountDue\":100,\"dueDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantWithReportsViewCanOpenReportsButWithoutItCannot() throws Exception {
        mvc.perform(get("/api/v1/reports/summary").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isForbidden());

        when(perm.has("REPORTS_VIEW")).thenReturn(true);
        mvc.perform(get("/api/v1/reports/summary").param("from", "2026-01-01").param("to", "2026-01-31"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadStudentAndBatchLookupsWhenGranted() throws Exception {
        when(perm.has("STUDENTS_VIEW")).thenReturn(true);
        when(perm.has("BATCHES_VIEW")).thenReturn(true);
        when(studentService.list(any(), any(), any())).thenReturn(Page.empty());
        when(batchService.list(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/students")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/batches")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantIsStillDeniedMarksEntryHomeworkAndStudentWrites() throws Exception {
        // Even if "perm" would say yes to everything, these are still on the
        // legacy role list, which has no ACCOUNTANT in it.
        when(perm.has(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        mvc.perform(post("/api/v1/tests/" + ID + "/marks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[{\"studentId\":\"" + ID + "\",\"marksObtained\":5}]}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/homework").contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchId\":\"" + ID + "\",\"title\":\"x\",\"dueDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/batches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Batch\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    void teacherStillPassesWhenPermissionIsGranted() throws Exception {
        when(perm.has("FEES_VIEW")).thenReturn(true);
        when(feeService.listPending(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/v1/fees/pending")).andExpect(status().isOk());
    }
}
