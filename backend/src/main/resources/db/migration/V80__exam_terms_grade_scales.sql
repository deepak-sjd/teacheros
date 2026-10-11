-- V80: Exam terms, grade scales, report card remarks (Dev 8, Task 3).
--
-- Term RESULTS are never stored - they are computed on demand from `marks`
-- (same rule as attendance %, ReportService, StudyPlanService). A test belongs
-- to a term when its test_date falls inside the term's [start_date, end_date].
-- Only the teacher's free-text remark per (student, term) is stored.
--
-- academic_year_id is deliberately a plain nullable UUID with NO foreign key:
-- Dev 2's academic-year table did not exist when this was written. Add the FK
-- in a follow-up migration once it does.

CREATE TABLE exam_terms (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    academic_year_id UUID,
    name             VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_exam_terms_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_exam_terms_tenant_start ON exam_terms(tenant_id, start_date);

-- One row per grade band. A tenant with NO rows here uses the CBSE-style
-- defaults held in GradeScaleService; saving a scale persists its own bands.
CREATE TABLE grade_scales (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    grade          VARCHAR(10) NOT NULL,
    min_percentage NUMERIC(5, 2) NOT NULL,
    max_percentage NUMERIC(5, 2) NOT NULL,
    remark         VARCHAR(100),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_grade_scales_tenant_grade UNIQUE (tenant_id, grade),
    CONSTRAINT chk_grade_scales_range CHECK (
        min_percentage >= 0 AND max_percentage <= 100 AND min_percentage <= max_percentage)
);
CREATE INDEX idx_grade_scales_tenant ON grade_scales(tenant_id, min_percentage);

CREATE TABLE report_card_remarks (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    student_id         UUID NOT NULL REFERENCES students(id),
    exam_term_id       UUID NOT NULL REFERENCES exam_terms(id),
    remark             VARCHAR(1000) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_report_card_remarks UNIQUE (tenant_id, student_id, exam_term_id)
);
CREATE INDEX idx_report_card_remarks_term ON report_card_remarks(tenant_id, exam_term_id);
