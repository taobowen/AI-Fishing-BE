-- Additive agent policy / learning stamps. Historical rows stay readable with NULL.
-- learning_algorithm_version is the formula/bucketing version (e.g. EmpiricalAlgorithm.VERSION).
-- learning_snapshot_version stays NULL until a true immutable evidence snapshot exists.
-- Keep existing prompt/tool/context/model columns.

ALTER TABLE agent_runs
    ADD COLUMN agent_policy_version VARCHAR(64),
    ADD COLUMN learning_algorithm_version VARCHAR(64),
    ADD COLUMN learning_snapshot_version VARCHAR(64);
