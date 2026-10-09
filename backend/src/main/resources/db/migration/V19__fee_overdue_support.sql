-- Serves the overdue sweep (status + due_date range scan).
CREATE INDEX idx_fees_status_due_date ON fees(status, due_date);

-- One-time backfill: nothing ever set OVERDUE before this migration.
UPDATE fees
SET status = 'OVERDUE'
WHERE status IN ('PENDING', 'PARTIALLY_PAID')
  AND due_date < (now() AT TIME ZONE 'Asia/Kolkata')::date;
