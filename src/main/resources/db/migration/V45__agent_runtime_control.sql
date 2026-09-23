-- Singleton live production/candidate switch. YAML remains the available catalog.
-- Changing this row must not rewrite in-flight or historical agent_runs.

CREATE TABLE agent_runtime_control (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    agent_enabled BOOLEAN NOT NULL,
    production_version VARCHAR(64) NOT NULL,
    candidate_version VARCHAR(64),
    shadow_enabled BOOLEAN NOT NULL,
    learning_enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO agent_runtime_control (
    id,
    agent_enabled,
    production_version,
    candidate_version,
    shadow_enabled,
    learning_enabled,
    updated_at
) VALUES (1, TRUE, 'v1', NULL, FALSE, TRUE, now());

-- Auditable skip reason for kill-switch runs that have no delivered-decision row.
ALTER TABLE agent_runs
    ADD COLUMN fallback_reason VARCHAR(64);
