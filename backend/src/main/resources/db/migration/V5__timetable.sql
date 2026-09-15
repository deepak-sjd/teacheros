CREATE TABLE timetable_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL REFERENCES batches(id),
    day_of_week SMALLINT NOT NULL,      -- 1=Monday .. 7=Sunday (ISO-8601)
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    subject VARCHAR(100),               -- optional override of batch.subject
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (day_of_week BETWEEN 1 AND 7),
    CHECK (end_time > start_time)
);

CREATE INDEX idx_timetable_slots_tenant_batch ON timetable_slots(tenant_id, batch_id);
