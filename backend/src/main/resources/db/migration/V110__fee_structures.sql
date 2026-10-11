-- V110: Fee heads, structures and instalment plans (Dev 12, Task 1).
--
-- A structure assigned to a student generates ordinary `fees` rows (one per
-- instalment), so the existing ledger / payment / overdue code keeps working.
-- Nothing existing is altered: only nullable or defaulted columns are added
-- to `fees`.

CREATE TABLE fee_heads (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL REFERENCES tenants(id),
    name                        VARCHAR(100) NOT NULL,
    code                        VARCHAR(30),
    sibling_concession_eligible BOOLEAN NOT NULL DEFAULT true,
    is_active                   BOOLEAN NOT NULL DEFAULT true,
    sort_order                  INT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_heads_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE instalment_plans (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(100) NOT NULL,
    number_of_instalments INT NOT NULL CHECK (number_of_instalments >= 1),
    is_active             BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_instalment_plans_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE instalment_plan_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    plan_id       UUID NOT NULL REFERENCES instalment_plans(id),
    instalment_no INT NOT NULL CHECK (instalment_no >= 1),
    due_date      DATE NOT NULL,
    percentage    NUMERIC(5, 2) NOT NULL CHECK (percentage > 0 AND percentage <= 100),
    CONSTRAINT uq_instalment_plan_items UNIQUE (plan_id, instalment_no)
);
CREATE INDEX idx_instalment_plan_items_plan ON instalment_plan_items(tenant_id, plan_id);

-- Scope: a batch (the app's "class"), a grade label, or both. The assign
-- wizard can also target explicit students regardless of scope.
CREATE TABLE fee_structures (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    name          VARCHAR(150) NOT NULL,
    academic_year VARCHAR(9) NOT NULL,           -- e.g. 2026-27
    grade         VARCHAR(50),
    batch_id      UUID REFERENCES batches(id),
    plan_id       UUID NOT NULL REFERENCES instalment_plans(id),
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_fee_structures_tenant_name_year UNIQUE (tenant_id, name, academic_year)
);
CREATE INDEX idx_fee_structures_tenant ON fee_structures(tenant_id);

CREATE TABLE fee_structure_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id),
    structure_id UUID NOT NULL REFERENCES fee_structures(id),
    head_id      UUID NOT NULL REFERENCES fee_heads(id),
    amount       NUMERIC(10, 2) NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_fee_structure_items UNIQUE (structure_id, head_id)
);
CREATE INDEX idx_fee_structure_items_structure ON fee_structure_items(tenant_id, structure_id);

-- Additive changes to the existing fees table. Every new column is nullable
-- or has a default, so existing rows and the existing Fee/Payment code are
-- unaffected (line_type defaults to STANDARD, discount_amount to 0).
--   fee_head_id    set only when the structure has exactly one head
--   gross_amount   amount before discounts (amount_due = gross - discount)
--   parent_fee_id  for LATE_FEE rows: the overdue instalment charged
ALTER TABLE fees
    ADD COLUMN fee_head_id     UUID REFERENCES fee_heads(id),
    ADD COLUMN structure_id    UUID REFERENCES fee_structures(id),
    ADD COLUMN instalment_no   INT,
    ADD COLUMN line_type       VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN gross_amount    NUMERIC(10, 2),
    ADD COLUMN discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN parent_fee_id   UUID REFERENCES fees(id);

-- Idempotency: one fee per (student, structure, instalment).
CREATE UNIQUE INDEX uq_fees_structure_instalment
    ON fees (tenant_id, student_id, structure_id, instalment_no)
    WHERE line_type = 'INSTALMENT' AND structure_id IS NOT NULL;

-- Late fee is charged at most once per overdue instalment.
CREATE UNIQUE INDEX uq_fees_late_fee_parent
    ON fees (parent_fee_id)
    WHERE line_type = 'LATE_FEE';

CREATE INDEX idx_fees_tenant_structure ON fees (tenant_id, structure_id) WHERE structure_id IS NOT NULL;
CREATE INDEX idx_fees_tenant_due ON fees (tenant_id, due_date);

-- Per-head breakdown of a generated fee. Payments are recorded against the
-- fee (not the head), so the "collection per fee head" report allocates each
-- payment across these lines pro rata to (gross - discount).
CREATE TABLE fee_line_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    fee_id          UUID NOT NULL REFERENCES fees(id),
    head_id         UUID NOT NULL REFERENCES fee_heads(id),
    gross_amount    NUMERIC(10, 2) NOT NULL,
    discount_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_fee_line_items UNIQUE (fee_id, head_id)
);
CREATE INDEX idx_fee_line_items_fee ON fee_line_items(tenant_id, fee_id);
