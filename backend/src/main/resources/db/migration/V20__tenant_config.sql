CREATE TABLE tenant_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    tenant_type     VARCHAR(20) NOT NULL DEFAULT 'COACHING',
    enabled_modules JSONB NOT NULL DEFAULT '[]'::jsonb,
    locale          VARCHAR(20) NOT NULL DEFAULT 'en-IN',
    timezone        VARCHAR(50) NOT NULL DEFAULT 'Asia/Kolkata',
    primary_color   VARCHAR(7),
    secondary_color VARCHAR(7),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_config_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_tenant_config_type CHECK (tenant_type IN ('COACHING', 'SCHOOL'))
);

-- One row per existing tenant, defaulting to COACHING with every current
-- module on. Keep this list in sync with TenantModules.ALL.
INSERT INTO tenant_config (tenant_id, enabled_modules)
SELECT t.id,
       '["ATTENDANCE","FEES","HOMEWORK","TIMETABLE","STUDY_MATERIAL","TESTS","ALERTS","MESSAGES","DOUBTS","REPORTS","ANALYTICS"]'::jsonb
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;
