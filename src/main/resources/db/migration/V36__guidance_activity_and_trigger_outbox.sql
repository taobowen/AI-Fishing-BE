-- Phase 3 activity state, BITE / fishInteractionId persistence, and crash-safe
-- trigger outbox. Schema and entities only; claimer is a later workstream.
-- USER_REQUEST stays synchronous and is excluded from outbox primary_trigger.

ALTER TABLE fishing_sessions
    ADD COLUMN activity_state VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN activity_state_since TIMESTAMPTZ,
    ADD COLUMN activity_state_source VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN activity_state_override VARCHAR(16),
    ADD COLUMN activity_state_override_since TIMESTAMPTZ;

UPDATE fishing_sessions
SET activity_state_since = started_at
WHERE activity_state_since IS NULL;

ALTER TABLE fishing_sessions
    ADD CONSTRAINT fishing_sessions_activity_state_check
        CHECK (activity_state IN ('TRANSIT', 'FISHING', 'PAUSED', 'UNKNOWN')),
    ADD CONSTRAINT fishing_sessions_activity_state_source_check
        CHECK (activity_state_source IN (
            'OVERRIDE', 'SESSION_PAUSE', 'EFFORT_SEGMENT', 'PROGRESS', 'INFERRED', 'UNKNOWN'
        )),
    ADD CONSTRAINT fishing_sessions_activity_state_override_check
        CHECK (
            activity_state_override IS NULL
            OR activity_state_override IN ('TRANSIT', 'FISHING', 'PAUSED')
        );

CREATE INDEX idx_fishing_sessions_active_activity
    ON fishing_sessions (status, activity_state)
    WHERE status = 'ACTIVE';

ALTER TABLE session_events
    ADD COLUMN fish_interaction_id UUID;

ALTER TABLE session_events
    DROP CONSTRAINT session_events_type_check;

ALTER TABLE session_events
    ADD CONSTRAINT session_events_type_check CHECK (type IN (
        'SESSION_STARTED', 'GPS_UPDATED', 'GPS_ACCURACY_DEGRADED', 'WEATHER_UPDATED',
        'WAYPOINT_ENTERED', 'WAYPOINT_LEFT', 'LURE_CHANGED', 'DEPTH_CHANGED',
        'RETRIEVE_CHANGED', 'ADVICE_CREATED', 'ADVICE_ACCEPTED', 'ADVICE_REJECTED',
        'USER_MOVED', 'BITE', 'FISH_ON', 'CATCH_CREATED', 'NO_BITE', 'PLAN_REPLANNED',
        'SAFETY_ALERT', 'SESSION_PAUSED', 'SESSION_RESUMED', 'SESSION_COMPLETED'
    ));

CREATE INDEX idx_session_events_fish_interaction
    ON session_events (fishing_session_id, fish_interaction_id)
    WHERE fish_interaction_id IS NOT NULL;

ALTER TABLE agent_runs
    DROP CONSTRAINT agent_runs_trigger_check;

ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_trigger_check CHECK (trigger IN (
        'USER_REQUEST', 'NO_BITE_THRESHOLD', 'FISH_ON', 'REPEATED_BITE_PATTERN',
        'WAYPOINT_REACHED', 'PLAN_STEP_COMPLETED', 'SIGNIFICANT_WEATHER_CHANGE',
        'SIGNIFICANT_LOCATION_CHANGE', 'CONSECUTIVE_FAILURE', 'SAFETY_STATE_CHANGED',
        'ROUTE_DEVIATION', 'RETURN_RISK_CHANGED'
    ));

CREATE TABLE guidance_trigger_outbox (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    primary_trigger VARCHAR(64) NOT NULL,
    related_triggers JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    claim_token UUID,
    CONSTRAINT guidance_trigger_outbox_primary_trigger_check CHECK (primary_trigger IN (
        'NO_BITE_THRESHOLD', 'FISH_ON', 'REPEATED_BITE_PATTERN',
        'WAYPOINT_REACHED', 'PLAN_STEP_COMPLETED', 'SIGNIFICANT_WEATHER_CHANGE',
        'SIGNIFICANT_LOCATION_CHANGE', 'CONSECUTIVE_FAILURE', 'SAFETY_STATE_CHANGED',
        'ROUTE_DEVIATION', 'RETURN_RISK_CHANGED'
    )),
    CONSTRAINT guidance_trigger_outbox_source_check CHECK (source IN ('EVENT', 'HEARTBEAT')),
    CONSTRAINT guidance_trigger_outbox_status_check CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'FAILED')),
    CONSTRAINT guidance_trigger_outbox_claim_token_key UNIQUE (claim_token)
);

-- One open row per session so later merge upserts this PENDING/CLAIMED row.
CREATE UNIQUE INDEX uq_guidance_trigger_outbox_session_open
    ON guidance_trigger_outbox (fishing_session_id)
    WHERE status IN ('PENDING', 'CLAIMED');

-- Stale CLAIMED rows are reclaimable by a later claimer (tactics-style).
CREATE INDEX idx_guidance_trigger_outbox_stale_claim
    ON guidance_trigger_outbox (status, claimed_at)
    WHERE status = 'CLAIMED';
