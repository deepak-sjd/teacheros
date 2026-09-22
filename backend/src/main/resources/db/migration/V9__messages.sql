-- V8: Parent-teacher messaging.
--
-- No separate "conversations" table - a thread is implicit: every message
-- with the same (tenant_id, student_id) IS the thread for that student.
-- Access is enforced the same way every other student-scoped endpoint in
-- this app is (StudentAccessGuard, see MessageService): staff can open any
-- thread in their tenant, a PARENT/STUDENT only their own child's/own
-- thread.
--
-- read_by_parent/read_by_staff are per-message, not per-user - same
-- accepted MVP simplification as notifications.is_read (see V7): a
-- student's guardian is effectively a single reader already, and "any
-- staff member has seen this" is good enough for a first pass on the
-- staff side too.
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    sender_role     VARCHAR(20) NOT NULL, -- PARENT or STAFF
    sender_name     VARCHAR(255) NOT NULL, -- denormalized display name at send time
    body            VARCHAR(2000) NOT NULL,
    read_by_parent  BOOLEAN NOT NULL DEFAULT false,
    read_by_staff   BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves "load this student's thread, oldest first" and both unread-count
-- queries - always filtered by tenant_id + student_id.
CREATE INDEX idx_messages_thread ON messages(tenant_id, student_id, created_at);
