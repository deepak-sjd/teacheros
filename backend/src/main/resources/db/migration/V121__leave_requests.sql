-- V121: Leave requests (Dev 13, Task 1).
--
-- A parent (or the student) asks for leave for a date range; staff approve
-- or reject. Approval marks the covered sessions EXCUSED (see
-- LeaveRequestService and AttendanceService#markAttendance) - nothing about
-- attendance percentage is stored here.
--
-- reason is free text from the family and may contain health details, so it
-- is returned only to staff and the student's own family and is never
-- written to audit_log metadata.
CREATE TABLE leave_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id),
    student_id           UUID NOT NULL REFERENCES students(id),
    from_date            DATE NOT NULL,
    to_date              DATE NOT NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by_user_id UUID NOT NULL REFERENCES users(id),
    decided_by_user_id   UUID REFERENCES users(id),
    decided_at           TIMESTAMPTZ,
    decision_note        VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_leave_dates CHECK (to_date >= from_date),
    CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- A student's own history, and the overlap check on submit.
CREATE INDEX idx_leave_requests_student ON leave_requests(tenant_id, student_id, from_date);
-- Staff "pending" list and the "who is on approved leave on this date" lookup.
CREATE INDEX idx_leave_requests_status ON leave_requests(tenant_id, status, from_date);
