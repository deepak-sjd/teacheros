-- V11: AI Doubt Solver.
--
-- One thread per student, same shape as V9's messages table - a "thread"
-- is implicit: every doubt_message row with the same (tenant_id,
-- student_id) IS that student's doubt-solving thread. sender is STUDENT
-- (the question, as typed) or AI (the generated answer) - there is no
-- STAFF/PARENT sender here, see DoubtService for who's allowed to ask
-- versus merely read the thread.
CREATE TABLE doubt_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    sender          VARCHAR(10) NOT NULL, -- STUDENT or AI
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves "load this student's thread, oldest first" and the daily
-- question-count check (DoubtService#assertUnderDailyLimit) - both always
-- filtered by tenant_id + student_id.
CREATE INDEX idx_doubt_messages_thread ON doubt_messages(tenant_id, student_id, created_at);
