-- V120: Student profile extras + documents (Dev 13, Task 1).
--
-- All new student columns are nullable so existing create/edit flows keep
-- working untouched. medical_notes is SENSITIVE: it is only ever returned
-- by the dedicated profile endpoint (never by list endpoints) and only to
-- staff or the student's own guardian - see StudentProfileService.
ALTER TABLE students ADD COLUMN previous_school   VARCHAR(255);
ALTER TABLE students ADD COLUMN medical_notes     TEXT;
ALTER TABLE students ADD COLUMN blood_group       VARCHAR(5);
ALTER TABLE students ADD COLUMN address           TEXT;
ALTER TABLE students ADD COLUMN guardian2_name     VARCHAR(255);
ALTER TABLE students ADD COLUMN guardian2_phone    VARCHAR(20);
ALTER TABLE students ADD COLUMN guardian2_relation VARCHAR(50);

-- File bytes live in object storage; this table holds the reference
-- (object_key) plus display metadata, same shape as study_material.
CREATE TABLE student_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    doc_type            VARCHAR(30) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    object_key          VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_student_documents_student ON student_documents(tenant_id, student_id, created_at);
