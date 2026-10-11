-- V72: Course chapters, lessons and per-student lesson progress.
--
-- Cascades run course -> chapter -> lesson -> progress so that deleting a
-- course (CourseService#delete) never fails on an FK and never orphans rows.
--
-- Video is NEVER hosted here: a lesson stores only a provider name and the
-- provider's own reference (video_provider/video_ref). Playback URLs are
-- signed on demand (see VideoPlaybackSigner) and never stored.
--
-- lesson_progress is "current state, not a log" - one row per (lesson,
-- student), same shape as study_plans (V17).
CREATE TABLE chapters (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    course_id   UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title       VARCHAR(255) NOT NULL,
    position    INT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chapters_course ON chapters(tenant_id, course_id, position);

CREATE TABLE lessons (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    course_id        UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    chapter_id       UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    type             VARCHAR(10) NOT NULL,          -- VIDEO, NOTES, LINK
    video_provider   VARCHAR(30),
    video_ref        VARCHAR(255),
    notes_text       TEXT,
    link_url         VARCHAR(500),
    duration_seconds INT,
    position         INT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lessons_course ON lessons(tenant_id, course_id);
CREATE INDEX idx_lessons_chapter ON lessons(tenant_id, chapter_id, position);

CREATE TABLE lesson_progress (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    lesson_id        UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES students(id),
    position_seconds INT NOT NULL DEFAULT 0,
    completed        BOOLEAN NOT NULL DEFAULT false,
    completed_at     TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lesson_progress_lesson_student UNIQUE (tenant_id, lesson_id, student_id)
);
CREATE INDEX idx_lesson_progress_student ON lesson_progress(tenant_id, student_id);
