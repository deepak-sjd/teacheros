-- Stable, human-quotable receipt number per payment. Existing payments are
-- backfilled in paid_at order; new rows get the next value from the sequence.
CREATE SEQUENCE fee_receipt_seq;

ALTER TABLE fee_payments ADD COLUMN receipt_number BIGINT;

UPDATE fee_payments p
SET receipt_number = r.rn
FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY paid_at, id) AS rn FROM fee_payments) r
WHERE p.id = r.id;

SELECT setval('fee_receipt_seq', COALESCE((SELECT MAX(receipt_number) FROM fee_payments), 0) + 1, false);

ALTER TABLE fee_payments ALTER COLUMN receipt_number SET DEFAULT nextval('fee_receipt_seq');
ALTER TABLE fee_payments ALTER COLUMN receipt_number SET NOT NULL;
ALTER TABLE fee_payments ADD CONSTRAINT uq_fee_payments_receipt_number UNIQUE (receipt_number);
