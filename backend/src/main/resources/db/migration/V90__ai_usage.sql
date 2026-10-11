-- V90: AI usage ledger + per-tenant daily limits (Dev 10).
--
-- One row per AI provider call, written BEFORE the call (status PENDING) and
-- finalised after (SUCCESS / FAILED). Failed and abandoned calls still count
-- against the daily limit - a looping client must not get free retries.
--
-- Deliberately stores NO prompt or answer text: prompts contain student
-- names, grades and marks. Only metadata is kept.
CREATE TABLE ai_usage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    feature         VARCHAR(30) NOT NULL,   -- DOUBT, STUDY_PLAN, CONTENT_QUESTIONS, CONTENT_NOTES
    provider        VARCHAR(20) NOT NULL,   -- log, ollama, anthropic
    model           VARCHAR(100),
    input_tokens    INT,                    -- null until a provider reports usage
    output_tokens   INT,
    status          VARCHAR(20) NOT NULL,   -- PENDING, SUCCESS, FAILED
    error_code      VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

-- Serves the daily-limit count (tenant + feature + created_at >= start of IST day)
-- and the Task 3 per-day dashboard.
CREATE INDEX idx_ai_usage_tenant_feature_created ON ai_usage(tenant_id, feature, created_at);
