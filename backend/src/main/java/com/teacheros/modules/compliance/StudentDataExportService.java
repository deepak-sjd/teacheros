package com.teacheros.modules.compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.tenant.CurrentTenantProvider;
import com.teacheros.modules.assessment.Assessment;
import com.teacheros.modules.assessment.AssessmentRepository;
import com.teacheros.modules.assessment.MarkRepository;
import com.teacheros.modules.attendance.AttendanceRepository;
import com.teacheros.modules.attendance.ClassSession;
import com.teacheros.modules.attendance.ClassSessionRepository;
import com.teacheros.modules.audit.AuditService;
import com.teacheros.modules.doubt.DoubtMessageRepository;
import com.teacheros.modules.fee.FeePayment;
import com.teacheros.modules.fee.FeePaymentRepository;
import com.teacheros.modules.fee.FeeRepository;
import com.teacheros.modules.message.MessageRepository;
import com.teacheros.modules.notification.NotificationRepository;
import com.teacheros.modules.student.Student;
import com.teacheros.modules.student.StudentAccessGuard;
import com.teacheros.modules.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP of one student's personal data as JSON files. Allowed for staff and for
 * the student's own guardian; a STUDENT account or any other parent gets a 404.
 */
@Service
public class StudentDataExportService {

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final ClassSessionRepository classSessionRepository;
    private final MarkRepository markRepository;
    private final AssessmentRepository assessmentRepository;
    private final FeeRepository feeRepository;
    private final FeePaymentRepository feePaymentRepository;
    private final MessageRepository messageRepository;
    private final DoubtMessageRepository doubtMessageRepository;
    private final NotificationRepository notificationRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final CurrentTenantProvider currentTenantProvider;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public StudentDataExportService(StudentRepository studentRepository, AttendanceRepository attendanceRepository,
            ClassSessionRepository classSessionRepository, MarkRepository markRepository,
            AssessmentRepository assessmentRepository, FeeRepository feeRepository,
            FeePaymentRepository feePaymentRepository, MessageRepository messageRepository,
            DoubtMessageRepository doubtMessageRepository, NotificationRepository notificationRepository,
            StudentAccessGuard studentAccessGuard, CurrentTenantProvider currentTenantProvider,
            AuditService auditService, ObjectMapper objectMapper) {
        this.studentRepository = studentRepository;
        this.attendanceRepository = attendanceRepository;
        this.classSessionRepository = classSessionRepository;
        this.markRepository = markRepository;
        this.assessmentRepository = assessmentRepository;
        this.feeRepository = feeRepository;
        this.feePaymentRepository = feePaymentRepository;
        this.messageRepository = messageRepository;
        this.doubtMessageRepository = doubtMessageRepository;
        this.notificationRepository = notificationRepository;
        this.studentAccessGuard = studentAccessGuard;
        this.currentTenantProvider = currentTenantProvider;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public byte[] export(UUID studentId) {
        String role = currentTenantProvider.getCurrentRole();
        if ("STUDENT".equals(role)) {
            throw new ResourceNotFoundException("Student", studentId);
        }
        studentAccessGuard.assertCanView(studentId); // 404 for another family's child
        UUID tenantId = currentTenantProvider.getCurrentTenantId();
        Student s = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        Map<String, Object> files = new LinkedHashMap<>();
        files.put("profile.json", profile(s));
        files.put("attendance.json", attendance(tenantId, studentId));
        files.put("marks.json", marks(tenantId, studentId));
        files.put("fees.json", fees(tenantId, studentId));
        files.put("messages.json", messages(tenantId, studentId));
        files.put("doubts.json", doubts(tenantId, studentId));
        files.put("notifications.json", notifications(tenantId, studentId));

        auditService.record(ComplianceAuditActions.DATA_EXPORTED, ComplianceAuditActions.STUDENT, studentId,
                AuditService.meta("studentId", studentId, "requestedByRole", role));
        return zip(files);
    }

    private Map<String, Object> profile(Student s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("fullName", s.getFullName());
        m.put("grade", s.getGrade());
        m.put("batchId", s.getBatchId());
        m.put("guardianPhone", s.getGuardianPhone());
        m.put("guardianEmail", s.getGuardianEmail());
        m.put("studentPhone", s.getPhoneNumber());
        m.put("enrolledAt", s.getCreatedAt());
        return m;
    }

    private List<Map<String, Object>> attendance(UUID tenantId, UUID studentId) {
        var records = attendanceRepository.findByTenantIdAndStudentIdOrderByMarkedAtDesc(tenantId, studentId);
        Map<UUID, ClassSession> sessions = classSessionRepository
                .findAllById(records.stream().map(a -> a.getClassSessionId()).distinct().toList()).stream()
                .collect(Collectors.toMap(ClassSession::getId, Function.identity()));
        return records.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            ClassSession cs = sessions.get(a.getClassSessionId());
            m.put("sessionDate", cs == null ? null : cs.getSessionDate());
            m.put("status", a.getStatus());
            m.put("markedAt", a.getMarkedAt());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> marks(UUID tenantId, UUID studentId) {
        return markRepository.findByTenantIdAndStudentIdOrderByTestDateDesc(tenantId, studentId).stream().map(mk -> {
            Assessment a = assessmentRepository.findByIdAndTenantId(mk.getTestId(), tenantId).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("test", a == null ? null : a.getTitle());
            m.put("subject", a == null ? null : a.getSubject());
            m.put("testDate", a == null ? null : a.getTestDate());
            m.put("marksObtained", mk.getMarksObtained());
            m.put("maxMarks", a == null ? null : a.getMaxMarks());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> fees(UUID tenantId, UUID studentId) {
        return feeRepository.findByTenantIdAndStudentId(tenantId, studentId).stream().map(f -> {
            List<FeePayment> payments = feePaymentRepository.findByTenantIdAndFeeId(tenantId, f.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("amountDue", f.getAmountDue());
            m.put("dueDate", f.getDueDate());
            m.put("status", f.getStatus());
            m.put("payments", payments.stream().map(p -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("amountPaid", p.getAmountPaid());
                pm.put("paidAt", p.getPaidAt());
                pm.put("method", p.getMethod());
                return pm;
            }).toList());
            return m;
        }).toList();
    }

    private List<Map<String, Object>> messages(UUID tenantId, UUID studentId) {
        return messageRepository.findByTenantIdAndStudentIdOrderByCreatedAtAsc(tenantId, studentId).stream()
                .map(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sentAt", x.getCreatedAt());
                    m.put("from", x.getSenderRole());
                    m.put("senderName", x.getSenderName());
                    m.put("body", x.getBody());
                    return m;
                }).toList();
    }

    private List<Map<String, Object>> doubts(UUID tenantId, UUID studentId) {
        return doubtMessageRepository.findByTenantIdAndStudentIdOrderByCreatedAtAsc(tenantId, studentId).stream()
                .map(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("at", x.getCreatedAt());
                    m.put("from", x.getSender());
                    m.put("body", x.getBody());
                    return m;
                }).toList();
    }

    private List<Map<String, Object>> notifications(UUID tenantId, UUID studentId) {
        return notificationRepository.findByTenantIdAndStudentIdOrderByCreatedAtDesc(tenantId, studentId).stream()
                .map(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("at", x.getCreatedAt());
                    m.put("type", x.getType());
                    m.put("title", x.getTitle());
                    m.put("message", x.getMessage());
                    return m;
                }).toList();
    }

    private byte[] zip(Map<String, Object> files) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Object> e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(e.getValue()));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build data export", e);
        }
    }
}
