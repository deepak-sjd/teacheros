-- V11: Online MCQ tests, alongside the existing offline (teacher-graded)
-- tests from V5.
--
-- A test is now either OFFLINE (unchanged behaviour: teacher records
-- marks manually via POST /tests/{id}/marks) or ONLINE_MCQ (student
-- answers multiple-choice questions themselves and the system grades
-- them). Both types share the same `tests` row and the same `marks`
-- table - an online test's auto-computed score is upserted into `marks`
-- exactly the way a teacher's manual entry would be, so report cards,
-- mark history, and ReportService all work for both kinds with no
-- changes on that side.
--
-- max_marks for an ONLINE_MCQ test is NOT authoritative at creation time
-- - it's recomputed by the application as the sum of that test's
-- question marks every time questions are (re)saved. The value the
-- teacher enters at creation is just a placeholder until then.
--
-- duration_minutes is advisory/client-enforced only (a UI countdown) -
-- the server does not reject a late submission on duration alone, so a
-- dropped connection near the end can't strand a student's attempt with
-- no way to submit. available_from/available_until IS server-enforced,
-- but only against starting a new attempt - an attempt already in
-- progress may still be submitted after the window closes, rather than
-- losing the student's answers outright.
ALTER TABLE tests ADD COLUMN test_type VARCHAR(20) NOT NULL DEFAULT 'OFFLINE';
ALTER TABLE tests ADD COLUMN duration_minutes INT;
ALTER TABLE tests ADD COLUMN available_from TIMESTAMPTZ;
ALTER TABLE tests ADD COLUMN available_until TIMESTAMPTZ;

CREATE TABLE test_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    test_id         UUID NOT NULL REFERENCES tests(id),
    question_text   VARCHAR(2000) NOT NULL,
    option_a        VARCHAR(500) NOT NULL,
    option_b        VARCHAR(500) NOT NULL,
    option_c        VARCHAR(500) NOT NULL,
    option_d        VARCHAR(500) NOT NULL,
    correct_option  CHAR(1) NOT NULL CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    marks           NUMERIC(6, 2) NOT NULL DEFAULT 1,
    position        INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_test_questions_test ON test_questions(tenant_id, test_id, position);

-- One attempt per (test, student) - no retakes in this first pass. The
-- unique constraint is also what makes "start" idempotent: a second
-- start() call for the same student just finds the existing row instead
-- of creating a duplicate.
CREATE TABLE test_attempts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    test_id         UUID NOT NULL REFERENCES tests(id),
    student_id      UUID NOT NULL REFERENCES students(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at    TIMESTAMPTZ,
    score           NUMERIC(6, 2),
    CONSTRAINT uq_test_attempts_test_student UNIQUE (tenant_id, test_id, student_id)
);
CREATE INDEX idx_test_attempts_student ON test_attempts(tenant_id, student_id);

CREATE TABLE test_answers (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    attempt_id        UUID NOT NULL REFERENCES test_attempts(id),
    question_id       UUID NOT NULL REFERENCES test_questions(id),
    selected_option   CHAR(1),
    is_correct        BOOLEAN,
    marks_awarded     NUMERIC(6, 2),
    CONSTRAINT uq_test_answers_attempt_question UNIQUE (tenant_id, attempt_id, question_id)
);
CREATE INDEX idx_test_answers_attempt ON test_answers(tenant_id, attempt_id);
