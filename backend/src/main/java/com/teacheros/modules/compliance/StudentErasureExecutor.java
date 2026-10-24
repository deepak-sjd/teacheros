package com.teacheros.modules.compliance;

import com.teacheros.common.exception.ResourceNotFoundException;
import com.teacheros.common.storage.StorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Anonymises one student and deletes their documents and communications.
 * Fees, fee payments, marks, attendance and test results are deliberately
 * untouched so accounting totals still reconcile - they carry no personal
 * fields, only the student id.
 *
 * Runs in the caller's transaction. Storage objects are deleted BEFORE their
 * rows: if the commit then fails, rows point at missing files (harmless),
 * whereas the reverse order could leave orphaned files nobody can find.
 */
@Component
public class StudentErasureExecutor {

    public static final String ERASED_NAME = "Deleted student";

    // Tables holding a student's personal communications, all keyed by (tenant_id,
    // student_id).
    // If a later module adds one (forum posts, leave requests, ...), add it here.
    private static final List<String> COMMUNICATION_TABLES = List.of("messages", "doubt_messages", "notifications",
            "study_plans");

    public record Result(int documentsDeleted, int communicationRowsDeleted, int usersDeactivated) {
    }

    private final JdbcTemplate jdbc;
    private final StorageService storageService;

    public StudentErasureExecutor(JdbcTemplate jdbc, StorageService storageService) {
        this.jdbc = jdbc;
        this.storageService = storageService;
    }

    public Result erase(UUID tenantId, UUID studentId) {
        List<String> phones = jdbc.query(
                "SELECT phone_number FROM students WHERE id = ? AND tenant_id = ?",
                (rs, i) -> rs.getString(1), studentId, tenantId);
        if (phones.isEmpty()) {
            throw new ResourceNotFoundException("Student", studentId);
        }
        String studentPhone = phones.get(0);

        int documents = deleteDocuments(tenantId, studentId);

        int comms = 0;
        for (String table : COMMUNICATION_TABLES) {
            comms += jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ? AND student_id = ?",
                    tenantId, studentId);
        }

        // The student's own login (if any). The guardian's account is kept - it may
        // serve other children.
        int users = 0;
        if (studentPhone != null && !studentPhone.isBlank()) {
            users = jdbc.update("UPDATE users SET is_active = false, display_name = ?, email = NULL "
                    + "WHERE tenant_id = ? AND phone_number = ? AND role = 'STUDENT'",
                    ERASED_NAME, tenantId, studentPhone);
        }

        jdbc.update("UPDATE consent_records SET guardian_phone = 'ERASED' WHERE tenant_id = ? AND student_id = ?",
                tenantId, studentId);
        jdbc.update("UPDATE students SET full_name = ?, grade = NULL, guardian_phone = 'ERASED', "
                + "phone_number = NULL, guardian_email = NULL, is_active = false, anonymised_at = now() "
                + "WHERE id = ? AND tenant_id = ?", ERASED_NAME, studentId, tenantId);

        return new Result(documents, comms, users);
    }

    // ASSUMPTION (verify against the baseline): table student_documents(tenant_id,
    // student_id, object_key).
    private int deleteDocuments(UUID tenantId, UUID studentId) {
        Boolean exists = jdbc.queryForObject("SELECT to_regclass('public.student_documents') IS NOT NULL",
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            return 0;
        }
        List<String> keys = jdbc.query(
                "SELECT object_key FROM student_documents WHERE tenant_id = ? AND student_id = ?",
                (rs, i) -> rs.getString(1), tenantId, studentId);
        for (String key : keys) {
            storageService.delete(key);
        }
        return jdbc.update("DELETE FROM student_documents WHERE tenant_id = ? AND student_id = ?",
                tenantId, studentId);
    }
}
