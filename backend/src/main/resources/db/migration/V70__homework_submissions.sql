-- V70: Homework submissions.
--
-- One row per (homework, student) - a resubmission before the due date
-- overwrites the row rather than adding history, same "current state, not
-- a log" shape as study_plans (V17). The file itself lives in object
-- storage (object_key only, never a URL), same rule as study_material (V4).
--
-- grade is a free-text label chosen by the teacher ("A", "8/10"), not a
-- number: it is deliberately NOT wired into marks/ranking/reports.
CREATE TABLE homework_submissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    homework_id         UUID NOT NULL REFERENCES homework(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    text                TEXT,
    object_key          VARCHAR(500),
    original_filename   VARCHAR(255),
    content_type        VARCHAR(100),
    file_size_bytes     BIGINT,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    status              VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED, REVIEWED
    teacher_feedback    TEXT,
    grade               VARCHAR(50),
    reviewed_at         TIMESTAMPTZ,
    reviewed_by_user_id UUID REFERENCES users(id),
    CONSTRAINT uq_homework_submissions_homework_student UNIQUE (tenant_id, homework_id, student_id)
);

-- Serves the teacher's "everyone's submission for this homework" list.
CREATE INDEX idx_homework_submissions_homework ON homework_submissions(tenant_id, homework_id);
