ALTER TABLE trip_planning_runs
    ADD COLUMN client_channel VARCHAR(16);

ALTER TABLE boats
    ADD COLUMN system_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN provenance VARCHAR(32);

CREATE UNIQUE INDEX boats_user_web_default_key
    ON boats (user_id)
    WHERE system_generated IS TRUE
      AND provenance = 'WEB_DEFAULT'
      AND active IS TRUE;

CREATE TABLE user_web_plan_entitlements (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    successful_generations INT NOT NULL DEFAULT 0,
    lifetime_limit INT NOT NULL DEFAULT 3,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_plan_generation_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(128) NOT NULL,
    trip_id UUID REFERENCES trips (id),
    planning_run_id UUID REFERENCES trip_planning_runs (id),
    trip_plan_id UUID REFERENCES trip_plans (id),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT web_plan_generation_requests_user_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_web_plan_generation_requests_plan
    ON web_plan_generation_requests (trip_plan_id);

CREATE TABLE web_plan_quota_ledger (
    planning_run_id UUID PRIMARY KEY REFERENCES trip_planning_runs (id),
    user_id UUID NOT NULL REFERENCES users (id),
    trip_plan_id UUID NOT NULL REFERENCES trip_plans (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_web_plan_quota_ledger_user ON web_plan_quota_ledger (user_id);
