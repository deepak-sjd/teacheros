-- V122: Link exam terms and fee structures to the real academic_years table.
--
-- This used to be at the end of V30, but exam_terms (V80) and fee_structures (V110)
-- are created after V30, so on a brand-new database V30 failed with
-- "relation exam_terms does not exist". Both tables exist by now.

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
