-- V3: Homework.
--
-- Homework belongs to a batch (not an individual student) - every student
-- in the batch sees the same assignment, matching how teachers described
-- assigning homework (per-class, not per-student). Study material file
-- attachments are a separate follow-up migration once object storage is
-- wired in - do not add a file/attachment column here.

CREATE TABLE homework (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    batch_id            UUID NOT NULL REFERENCES batches(id),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    due_date            DATE NOT NULL,
    created_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_homework_tenant_batch ON homework(tenant_id, batch_id, due_date);
