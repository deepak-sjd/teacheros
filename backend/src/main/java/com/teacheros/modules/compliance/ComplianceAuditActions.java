package com.teacheros.modules.compliance;

public final class ComplianceAuditActions {

    private ComplianceAuditActions() {
    }

    public static final String CONSENT_GRANTED = "CONSENT_GRANTED";
    public static final String CONSENT_WITHDRAWN = "CONSENT_WITHDRAWN";
    public static final String DATA_EXPORTED = "STUDENT_DATA_EXPORTED";
    public static final String ERASURE_REQUESTED = "ERASURE_REQUESTED";
    public static final String ERASURE_APPROVED = "ERASURE_APPROVED";
    public static final String ERASURE_REJECTED = "ERASURE_REJECTED";
    public static final String ERASURE_DOCUMENTS_DELETED = "ERASURE_DOCUMENTS_DELETED";
    public static final String ERASURE_COMMUNICATIONS_DELETED = "ERASURE_COMMUNICATIONS_DELETED";
    public static final String ERASURE_STUDENT_ANONYMISED = "ERASURE_STUDENT_ANONYMISED";
    public static final String ERASURE_COMPLETED = "ERASURE_COMPLETED";
    public static final String ERASURE_FAILED = "ERASURE_FAILED";

    public static final String CONSENT = "CONSENT";
    public static final String STUDENT = "STUDENT";
    public static final String ERASURE_REQUEST = "ERASURE_REQUEST";
}
