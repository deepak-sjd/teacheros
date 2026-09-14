-- V1: Initial schema for TeacherOS MVP
-- Scope: tenants, users, students, parents, batches, class sessions,
-- attendance, fees, fee payments, refresh tokens, audit log.
--
-- Design rules followed:
--  * every tenant-owned table carries tenant_id (never nullable)
--  * soft delete via is_active / deleted_at where records must be retained
--    for historical integrity (students, batches)
--  * attendance percentage is NEVER stored - always derived at query time
--  * no plaintext secrets stored anywhere in this schema

CREATE TABLE tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active     BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    phone_number    VARCHAR(20) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    role            VARCHAR(30) NOT NULL, -- OWNER, TEACHER, PARENT, STUDENT, STAFF, ADMIN
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ,
    CONSTRAINT uq_users_tenant_phone UNIQUE (tenant_id, phone_number)
);
CREATE INDEX idx_users_tenant ON users(tenant_id);

-- One-time codes for phone OTP login. Never store the OTP anywhere else
-- (not in logs, not in audit_log). Rows expire and are deleted by a cleanup job.
CREATE TABLE otp_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(20) NOT NULL,
    code_hash       VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    attempt_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_otp_codes_phone ON otp_codes(phone_number);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    subject         VARCHAR(255),
    schedule_summary VARCHAR(255), -- human-readable e.g. "Mon/Wed/Fri 5-6pm"; structured timetable is a later phase
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_batches_tenant ON batches(tenant_id);

CREATE TABLE students (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    batch_id        UUID REFERENCES batches(id),
    full_name       VARCHAR(255) NOT NULL,
    grade           VARCHAR(50),
    guardian_phone  VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_students_tenant_batch ON students(tenant_id, batch_id);

CREATE TABLE parents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    user_id         UUID REFERENCES users(id), -- nullable: a parent may not have logged in yet
    full_name       VARCHAR(255),
    phone_number    VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_parents_tenant ON parents(tenant_id);

CREATE TABLE student_parents (
    student_id      UUID NOT NULL REFERENCES students(id),
    parent_id       UUID NOT NULL REFERENCES parents(id),
    PRIMARY KEY (student_id, parent_id)
);

CREATE TABLE class_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    batch_id        UUID NOT NULL REFERENCES batches(id),
    session_date    DATE NOT NULL,
    is_cancelled    BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_class_sessions_batch_date UNIQUE (tenant_id, batch_id, session_date)
);
CREATE INDEX idx_class_sessions_tenant_batch ON class_sessions(tenant_id, batch_id);

CREATE TABLE attendance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    class_session_id    UUID NOT NULL REFERENCES class_sessions(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    status              VARCHAR(20) NOT NULL, -- PRESENT, ABSENT, LATE
    marked_by_user_id   UUID NOT NULL REFERENCES users(id),
    marked_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attendance_session_student UNIQUE (class_session_id, student_id)
);
CREATE INDEX idx_attendance_tenant_student ON attendance(tenant_id, student_id);

CREATE TABLE fees (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    amount_due      NUMERIC(10, 2) NOT NULL,
    due_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PARTIALLY_PAID, PAID, OVERDUE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fees_tenant_student_status ON fees(tenant_id, student_id, status);

CREATE TABLE fee_payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    fee_id          UUID NOT NULL REFERENCES fees(id),
    amount_paid     NUMERIC(10, 2) NOT NULL,
    paid_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    method          VARCHAR(30) -- CASH, UPI, BANK_TRANSFER, OTHER
);
CREATE INDEX idx_fee_payments_tenant_fee ON fee_payments(tenant_id, fee_id);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    actor_user_id   UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     UUID,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_tenant ON audit_log(tenant_id, created_at);
