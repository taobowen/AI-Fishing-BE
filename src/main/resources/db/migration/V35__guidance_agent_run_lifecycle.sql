-- RUNNING rows may exist before state/context are built (safety BLOCK can
-- finalize without ever writing context). TIMEOUT is a first-class audit status.

ALTER TABLE agent_runs
    DROP CONSTRAINT agent_runs_status_check;

ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_status_check
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FALLBACK', 'FAILED', 'TIMEOUT'));

ALTER TABLE agent_runs
    ALTER COLUMN state_snapshot DROP NOT NULL;

ALTER TABLE agent_runs
    ALTER COLUMN context_snapshot DROP NOT NULL;
