-- V61: Course catalogue.
-- Staff manage courses; the public site only ever reads is_published = true rows
-- (enforced in PublicCourseService, via dedicated *PublishedTrue repository finders).
CREATE TABLE courses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    price           NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    syllabus        TEXT,                       -- one topic per line
    demo_video_url  VARCHAR(500),
    batch_id        UUID REFERENCES batches(id), -- the batch a student joins on enrolment (used by checkout later)
    is_published    BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_courses_tenant_published ON courses(tenant_id, is_published, created_at DESC);
