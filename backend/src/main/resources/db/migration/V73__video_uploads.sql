-- V73: Staff video uploads (Dev 7, Phase 2 Task 1).
--
-- One row per video a tenant has sent to the managed video provider. The
-- video bytes NEVER touch this server or our database: the browser uploads
-- straight to the provider, and we keep only the provider's own reference
-- (video_ref) plus metadata. A lesson later points at (provider, video_ref)
-- through the existing lessons.video_provider / lessons.video_ref columns.
--
-- size_bytes: the size the client DECLARED when starting the upload (used for
-- the per-tenant storage cap check), replaced by the provider's real stored
-- size once the video finishes processing. FAILED rows do not count towards
-- the cap.
CREATE TABLE video_uploads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    provider            VARCHAR(30) NOT NULL,
    video_ref           VARCHAR(255) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL,   -- PENDING_UPLOAD, PROCESSING, READY, FAILED
    size_bytes          BIGINT NOT NULL DEFAULT 0,
    duration_seconds    INT,
    failure_reason      VARCHAR(255),
    created_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_video_uploads_provider_ref UNIQUE (provider, video_ref)
);

-- Serves the staff library list and the per-tenant storage sum.
CREATE INDEX idx_video_uploads_tenant ON video_uploads(tenant_id, created_at DESC);
