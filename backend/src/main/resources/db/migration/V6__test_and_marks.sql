-- V5: Tests and marks.
--
-- A test belongs to a batch (like homework/study material) - its metadata
-- (title, subject, date, max marks) is visible to the whole batch, same
-- visibility rule as homework, enforced by BatchAccessGuard.
--
-- Marks are per (test, student) and are NOT batch-visible the same way -
-- a parent/student must only ever see their OWN marks, never a
-- classmate's. This is enforced at the service layer via
-- StudentAccessGuard on the per-student marks endpoint; the "list all
-- marks for a test" endpoint (used for grading) is staff-only at the
-- controller level, not just filtered.
--
-- marks_obtained is nullable - a Mark row can exist for a student before
-- grading happens (created when the teacher opens the grading screen) or
-- simply never be created until graded. Both are valid; ungraded is
-- represented as either "no row" or "row with NULL marks_obtained" -
-- the application always upserts, so either is handled correctly.

CREATE TABLE tests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    batch_id            UUID NOT NULL REFERENCES batches(id),
    title               VARCHAR(255) NOT NULL,
    subject             VARCHAR(255),
    max_marks           NUMERIC(6, 2) NOT NULL,
    test_date           DATE NOT NULL,
    created_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tests_tenant_batch ON tests(tenant_id, batch_id, test_date);

CREATE TABLE marks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    test_id         UUID NOT NULL REFERENCES tests(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    marks_obtained  NUMERIC(6, 2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_marks_test_student UNIQUE (test_id, student_id)
);
CREATE INDEX idx_marks_tenant_student ON marks(tenant_id, student_id);
