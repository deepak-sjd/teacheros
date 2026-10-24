-- V62: Demo class booking + follow-up reminder tracking.

CREATE TABLE demo_slots (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    course_id        UUID REFERENCES courses(id),
    batch_id         UUID REFERENCES batches(id),
    starts_at        TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30 CHECK (duration_minutes BETWEEN 5 AND 480),
    capacity         INT NOT NULL CHECK (capacity BETWEEN 1 AND 500),
    location         VARCHAR(255),
    is_cancelled     BOOLEAN NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A slot is for a course OR a batch OR neither (a general demo), never both.
    CONSTRAINT ck_demo_slot_one_target CHECK (course_id IS NULL OR batch_id IS NULL)
);
CREATE INDEX idx_demo_slots_tenant_start ON demo_slots(tenant_id, starts_at);

CREATE TABLE demo_bookings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    slot_id     UUID NOT NULL REFERENCES demo_slots(id),
    lead_id     UUID NOT NULL REFERENCES leads(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'BOOKED',  -- BOOKED, SHOWED, NO_SHOW, CANCELLED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One row per (slot, lead): a cancelled booking is re-activated rather than duplicated.
    CONSTRAINT uq_demo_booking_slot_lead UNIQUE (slot_id, lead_id)
);
CREATE INDEX idx_demo_bookings_tenant_slot ON demo_bookings(tenant_id, slot_id);
CREATE INDEX idx_demo_bookings_tenant_lead ON demo_bookings(tenant_id, lead_id);

-- Which next_follow_up date a reminder was already sent for. The reminder job claims a
-- lead with an atomic UPDATE on this column, so a lead is reminded once per follow-up date.
ALTER TABLE leads ADD COLUMN follow_up_reminded_for DATE;
CREATE INDEX idx_leads_follow_up_due ON leads(next_follow_up) WHERE next_follow_up IS NOT NULL;
