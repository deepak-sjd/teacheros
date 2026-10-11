-- Announcements / circulars with read receipts.
--
-- audience: ALL (every account in the tenant), BATCH (students in batch_id),
-- CLASS (students whose grade matches `grade`). created_by_name is
-- denormalized at send time, same reasoning as messages.sender_name.
--
-- Reads are per user account (a parent and a student on the same family are
-- separate readers). The unique constraint makes mark-read idempotent.
CREATE TABLE announcements (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    title               VARCHAR(200) NOT NULL,
    body                TEXT NOT NULL,
    audience            VARCHAR(10) NOT NULL,
    batch_id            UUID REFERENCES batches(id),
    grade               VARCHAR(50),
    created_by_user_id  UUID NOT NULL REFERENCES users(id),
    created_by_name     VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_announcements_audience CHECK (
        (audience = 'ALL'   AND batch_id IS NULL AND grade IS NULL) OR
        (audience = 'BATCH' AND batch_id IS NOT NULL AND grade IS NULL) OR
        (audience = 'CLASS' AND grade IS NOT NULL AND batch_id IS NULL)
    )
);
CREATE INDEX idx_announcements_tenant_created ON announcements(tenant_id, created_at DESC);

CREATE TABLE announcement_reads (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    announcement_id  UUID NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES users(id),
    read_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_announcement_reads UNIQUE (announcement_id, user_id)
);
CREATE INDEX idx_announcement_reads_user ON announcement_reads(tenant_id, user_id);
