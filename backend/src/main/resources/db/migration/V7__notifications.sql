CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    student_id UUID NOT NULL REFERENCES students(id),
    type VARCHAR(30) NOT NULL,          -- LOW_ATTENDANCE, FEE_DUE, FEE_OVERDUE
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Powers both the dedupe check (don't re-alert the same student/type too
-- often) and the parent/student feed read (newest first for one student).
CREATE INDEX idx_notifications_tenant_student_type_created
    ON notifications(tenant_id, student_id, type, created_at DESC);
