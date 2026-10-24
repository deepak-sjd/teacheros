CREATE TABLE consent_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    guardian_phone  VARCHAR(20) NOT NULL,
    purpose         VARCHAR(50) NOT NULL,
    text_version    VARCHAR(20) NOT NULL,
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawn_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_consent_records_student ON consent_records(tenant_id, student_id, purpose);
-- One active (not withdrawn) consent per student/guardian/purpose.
CREATE UNIQUE INDEX uq_consent_records_active
    ON consent_records(tenant_id, student_id, guardian_phone, purpose) WHERE withdrawn_at IS NULL;
