-- Session replay correlation and SHADOW isolation.
-- Production readers of agent_runs must filter visibility = PRODUCTION.
-- SHADOW never writes agent_delivered_decisions, guidance_plan_versions, or learning.

ALTER TABLE agent_runs
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRODUCTION',
    ADD COLUMN trigger_outbox_id UUID REFERENCES guidance_trigger_outbox (id),
    ADD COLUMN related_triggers JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN trigger_reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_visibility_check
        CHECK (visibility IN ('PRODUCTION', 'SHADOW'));

CREATE INDEX idx_agent_runs_session_visibility_started
    ON agent_runs (fishing_session_id, visibility, started_at DESC);

CREATE INDEX idx_agent_runs_trigger_outbox_id
    ON agent_runs (trigger_outbox_id)
    WHERE trigger_outbox_id IS NOT NULL;
