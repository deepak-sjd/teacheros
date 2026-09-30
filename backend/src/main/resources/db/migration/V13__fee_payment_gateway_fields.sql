-- Lets an online payment be traced back to its Razorpay order/payment, and
-- lets PaymentService detect a duplicate verify call (Razorpay's checkout
-- can retry the success callback) without recording the same payment twice.
ALTER TABLE fee_payments ADD COLUMN gateway_order_id VARCHAR(255);
ALTER TABLE fee_payments ADD COLUMN gateway_payment_id VARCHAR(255);
CREATE UNIQUE INDEX uq_fee_payments_gateway_payment_id ON fee_payments (gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;
