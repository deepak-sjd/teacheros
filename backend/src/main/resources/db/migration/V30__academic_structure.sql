-- V30: Academic structure for SCHOOL tenants (Dev 2).
--
-- Years -> classes -> sections -> subjects -> per-section subject teachers,
-- plus a placement (section, roll number, year) on students. Everything new
-- is nullable or additive, so COACHING tenants and existing rows are untouched.
--
-- Tenant consistency between a child row and its parent is enforced in the
-- services (findByIdAndTenantId), same as every other module.

CREATE TABLE academic_years (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        VARCHAR(9) NOT NULL,                 -- e.g. 2026-27
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    is_current  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_academic_years_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_academic_years_dates CHECK (end_date > start_date)
);
-- At most one current year per tenant.
CREATE UNIQUE INDEX uq_academic_years_one_current ON academic_years (tenant_id) WHERE is_current;

CREATE TABLE school_classes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    name          VARCHAR(50) NOT NULL,              -- e.g. Class 8
    display_order INT NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_school_classes_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE sections (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    class_id              UUID NOT NULL REFERENCES school_classes(id),
    name                  VARCHAR(20) NOT NULL,      -- e.g. A
    class_teacher_user_id UUID REFERENCES users(id),
    capacity              INT CHECK (capacity IS NULL OR capacity > 0),
    is_active             BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sections_class_name UNIQUE (class_id, name)
);
CREATE INDEX idx_sections_tenant_class ON sections(tenant_id, class_id);

CREATE TABLE subjects (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(20),
    is_active  BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subjects_tenant_name UNIQUE (tenant_id, name)
);
CREATE UNIQUE INDEX uq_subjects_tenant_code ON subjects (tenant_id, code) WHERE code IS NOT NULL;

-- One teacher per (section, subject); assigning again replaces the teacher.
CREATE TABLE section_subject_teachers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    section_id      UUID NOT NULL REFERENCES sections(id),
    subject_id      UUID NOT NULL REFERENCES subjects(id),
    teacher_user_id UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_section_subject_teachers UNIQUE (section_id, subject_id)
);
CREATE INDEX idx_section_subject_teachers_section ON section_subject_teachers(tenant_id, section_id);

-- Student placement. All nullable, so COACHING students and existing create/
-- edit flows are unaffected.
ALTER TABLE students
    ADD COLUMN section_id       UUID REFERENCES sections(id),
    ADD COLUMN roll_number      VARCHAR(20),
    ADD COLUMN academic_year_id UUID REFERENCES academic_years(id);

-- Roll number unique per section and year. Only active students hold a number,
-- so deactivating a leaver frees theirs.
CREATE UNIQUE INDEX uq_students_roll_per_section_year
    ON students (tenant_id, section_id, academic_year_id, roll_number)
    WHERE is_active = true
      AND section_id IS NOT NULL
      AND academic_year_id IS NOT NULL
      AND roll_number IS NOT NULL;
CREATE INDEX idx_students_tenant_section ON students (tenant_id, section_id) WHERE section_id IS NOT NULL;

-- Link the loose year values from earlier migrations to the real table.
-- NOT VALID: existing exam_terms rows are not checked (or changed); the
-- constraint applies to rows inserted or re-pointed from now on.
ALTER TABLE exam_terms
    ADD CONSTRAINT fk_exam_terms_academic_year
    FOREIGN KEY (academic_year_id) REFERENCES academic_years(id) NOT VALID;

-- fee_structures keeps its text academic_year ("2026-27"); this optional
-- column lets a structure also point at the real year.
ALTER TABLE fee_structures ADD COLUMN academic_year_id UUID REFERENCES academic_years(id);
CREATE INDEX idx_fee_structures_academic_year ON fee_structures (tenant_id, academic_year_id) WHERE academic_year_id IS NOT NULL;
