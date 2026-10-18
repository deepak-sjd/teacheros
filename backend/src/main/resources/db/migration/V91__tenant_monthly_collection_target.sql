-- V91: Optional monthly fee-collection target per tenant (Dev 10).
-- NULL = no target set. Stored on tenants (same pattern as V18's logo column);
-- it is a setting, not a derived number - the "collected" side is always
-- computed from fee_payments at read time.
ALTER TABLE tenants ADD COLUMN monthly_collection_target NUMERIC(12, 2);
ALTER TABLE tenants ADD CONSTRAINT chk_tenants_monthly_collection_target
    CHECK (monthly_collection_target IS NULL OR monthly_collection_target >= 0);
