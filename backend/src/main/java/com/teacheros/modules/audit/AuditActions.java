package com.teacheros.modules.audit;

/**
 * Action and resource-type names written to audit_log. Add new ones here, never
 * inline strings.
 */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String FEE_CREATED = "FEE_CREATED";
    public static final String FEE_PAYMENT_RECORDED = "FEE_PAYMENT_RECORDED";
    public static final String MARKS_SUBMITTED = "MARKS_SUBMITTED";
    public static final String TEST_DELETED = "TEST_DELETED";
    public static final String STUDENT_DEACTIVATED = "STUDENT_DEACTIVATED";
    public static final String BATCH_DEACTIVATED = "BATCH_DEACTIVATED";

    // Staff and permission administration (Dev 11)
    public static final String STAFF_CREATED = "STAFF_CREATED";
    public static final String STAFF_ROLE_CHANGED = "STAFF_ROLE_CHANGED";
    public static final String STAFF_DEACTIVATED = "STAFF_DEACTIVATED";
    public static final String STAFF_REACTIVATED = "STAFF_REACTIVATED";
    public static final String PERMISSION_CHANGED = "PERMISSION_CHANGED";

    // Video library
    public static final String VIDEO_UPLOAD_CREATED = "VIDEO_UPLOAD_CREATED";
    public static final String VIDEO_UPLOAD_DELETED = "VIDEO_UPLOAD_DELETED";

    // Leave requests
    public static final String LEAVE_DECIDED = "LEAVE_DECIDED";

    public static final String FEE = "FEE";
    public static final String TEST = "TEST";
    public static final String STUDENT = "STUDENT";
    public static final String BATCH = "BATCH";
    public static final String STAFF = "STAFF";
    public static final String ROLE_PERMISSION = "ROLE_PERMISSION";
    public static final String VIDEO_UPLOAD = "VIDEO_UPLOAD";
    public static final String LEAVE_REQUEST = "LEAVE_REQUEST";
}
