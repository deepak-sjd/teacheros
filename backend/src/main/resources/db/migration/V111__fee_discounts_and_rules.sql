-- V111: Discounts, sibling concession and late-fee rules (Dev 12, Task 2).

-- A discount applies to FUTURE assignments only; it never rewrites fees that
-- were already generated. fee_head_id NULL = applies across all heads.
CREATE TABLE fee_discounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    student_id          UUID NOT NULL REFERENCES students(id),
    fee_head_id         UUID REFERENCES fee_heads(id),
    discount_type       VARCHAR(10) NOT NULL CHECK (discount_type IN ('FIXED', 'PERCENT')),
    value               NUMERIC(10, 2) NOT NULL CHECK (value > 0),
    reason              VARCHAR(255) NOT NULL,
    approved_by_user_id UUID NOT NULL REFERENCES users(id),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fee_discounts_tenant_student ON fee_discounts(tenant_id, student_id);

-- One row per tenant (tenant_id is the key). No row = all rules off.
CREATE TABLE fee_rules (
    tenant_id                UUID PRIMARY KEY REFERENCES tenants(id),
    sibling_concession_type  VARCHAR(10) NOT NULL DEFAULT 'NONE'
        CHECK (sibling_concession_type IN ('NONE', 'FIXED', 'PERCENT')),
    sibling_concession_value NUMERIC(10, 2) NOT NULL DEFAULT 0,
    late_fee_type            VARCHAR(10) NOT NULL DEFAULT 'NONE'
        CHECK (late_fee_type IN ('NONE', 'FIXED', 'PERCENT')),
    late_fee_value           NUMERIC(10, 2) NOT NULL DEFAULT 0,
    late_fee_grace_days      INT NOT NULL DEFAULT 0 CHECK (late_fee_grace_days >= 0),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id       UUID REFERENCES users(id)
);
