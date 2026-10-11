-- V60: Lead pipeline + public tenant slug.

-- Public tenant identifier used by unauthenticated endpoints (never a tenant UUID).
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS slug VARCHAR(80);

UPDATE tenants
SET slug = left(coalesce(nullif(trim(both '-' from lower(regexp_replace(name, '[^a-zA-Z0-9]+', '-', 'g'))), ''), 'school'), 60)
           || '-' || substr(replace(id::text, '-', ''), 1, 6)
WHERE slug IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenants_slug ON tenants(slug);

CREATE TABLE leads (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(255) NOT NULL,
    phone                 VARCHAR(20) NOT NULL,
    email                 VARCHAR(255),
    message               VARCHAR(2000),
    source                VARCHAR(30) NOT NULL DEFAULT 'WEBSITE',  -- WEBSITE, COURSE_PAGE, WALK_IN, REFERRAL, OTHER
    status                VARCHAR(20) NOT NULL DEFAULT 'NEW',      -- NEW, CONTACTED, DEMO, ENROLLED, LOST
    assigned_to           UUID REFERENCES users(id),
    next_follow_up        DATE,
    converted_student_id  UUID REFERENCES students(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_leads_tenant_status ON leads(tenant_id, status, created_at DESC);
CREATE INDEX idx_leads_tenant_assigned ON leads(tenant_id, assigned_to);
CREATE INDEX idx_leads_tenant_phone ON leads(tenant_id, phone, created_at DESC);

-- Append-only timeline. from_status is NULL for the first (intake) row;
-- from_status = to_status rows are notes (assignment, follow-up).
CREATE TABLE lead_status_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    lead_id             UUID NOT NULL REFERENCES leads(id),
    from_status         VARCHAR(20),
    to_status           VARCHAR(20) NOT NULL,
    note                VARCHAR(500),
    changed_by_user_id  UUID REFERENCES users(id),  -- NULL = the public enquiry itself
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lead_history_lead ON lead_status_history(tenant_id, lead_id, changed_at);
