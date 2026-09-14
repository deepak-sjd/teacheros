-- V4: Study material.
--
-- Like homework, study material belongs to a batch (not an individual
-- student) - the file itself lives in object storage (MinIO locally, a
-- real S3-compatible provider in production); this table only stores the
-- reference (object_key) plus metadata needed to list/display it.
-- Never store file bytes in Postgres.

CREATE TABLE study_material (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    batch_id            UUID NOT NULL REFERENCES batches(id),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    object_key          VARCHAR(500) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    file_size_bytes      BIGINT NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_study_material_tenant_batch ON study_material(tenant_id, batch_id, created_at);
