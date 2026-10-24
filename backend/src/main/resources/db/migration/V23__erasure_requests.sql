ALTER TABLE students ADD COLUMN anonymised_at TIMESTAMPTZ;

CREATE TABLE erasure_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    student_id            UUID NOT NULL REFERENCES students(id),
    requested_by_user_id  UUID NOT NULL REFERENCES users(id),
    requested_by_role     VARCHAR(20) NOT NULL,
    reason                VARCHAR(1000),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, COMPLETED, FAILED
    decided_by_user_id    UUID REFERENCES users(id),
    decided_at            TIMESTAMPTZ,
    decision_note         VARCHAR(1000),
    completed_at          TIMESTAMPTZ,
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_erasure_requests_tenant_status ON erasure_requests(tenant_id, status, created_at DESC);
CREATE INDEX idx_erasure_requests_student ON erasure_requests(tenant_id, student_id);
-- Only one open request per student.
CREATE UNIQUE INDEX uq_erasure_requests_open
    ON erasure_requests(tenant_id, student_id) WHERE status IN ('PENDING', 'APPROVED', 'FAILED');
