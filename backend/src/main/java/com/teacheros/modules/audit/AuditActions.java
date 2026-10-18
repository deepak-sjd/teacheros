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
    public static final String LEAVE_DECIDED = "LEAVE_DECIDED";

    public static final String FEE = "FEE";
    public static final String TEST = "TEST";
    public static final String STUDENT = "STUDENT";
    public static final String BATCH = "BATCH";
    public static final String LEAVE_REQUEST = "LEAVE_REQUEST";
}
