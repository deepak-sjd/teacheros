-- V71: Live-class join tracking.
--
-- One row per (timetable slot, student, date) - a "session" is a recurring
-- weekly slot on a specific date, there is no class_session row for it
-- until attendance is marked. Repeat clicks keep the first row, so this
-- records "first joined at", not every click.
--
-- This is a log only. It deliberately does NOT write to attendance.
--
-- ON DELETE CASCADE on the slot: TimetableService#delete removes slots, and
-- a join log has no meaning without its slot.
CREATE TABLE class_join_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    timetable_slot_id   UUID NOT NULL REFERENCES timetable_slots(id) ON DELETE CASCADE,
    student_id          UUID NOT NULL REFERENCES students(id),
    session_date        DATE NOT NULL,
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_class_join_logs_session_student
        UNIQUE (tenant_id, timetable_slot_id, student_id, session_date)
);

-- Serves the teacher's "who joined this session" list.
CREATE INDEX idx_class_join_logs_session ON class_join_logs(tenant_id, timetable_slot_id, session_date);
