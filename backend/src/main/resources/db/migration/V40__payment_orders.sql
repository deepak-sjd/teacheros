-- Persisted payment orders. verify() credits the order's own amount, never
-- the fee's outstanding balance. gateway_order_id is globally unique (the
-- Razorpay webhook has no JWT, so it must look an order up without a tenant).
CREATE TABLE payment_orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    fee_id            UUID NOT NULL REFERENCES fees(id),
    gateway_order_id  VARCHAR(255) NOT NULL,
    amount_in_paise   BIGINT NOT NULL CHECK (amount_in_paise > 0),
    status            VARCHAR(20) NOT NULL DEFAULT 'CREATED', -- CREATED, PAID, FAILED
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_orders_gateway_order UNIQUE (gateway_order_id)
);
CREATE INDEX idx_payment_orders_tenant_fee ON payment_orders(tenant_id, fee_id);
