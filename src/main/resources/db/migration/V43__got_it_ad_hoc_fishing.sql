-- Additive Got it + ad-hoc fishing contracts. Guidance schema stays v1.

ALTER TABLE agent_feedback
    DROP CONSTRAINT agent_feedback_status_check;

ALTER TABLE agent_feedback
    ADD CONSTRAINT agent_feedback_status_check CHECK (status IN (
        'ACCEPTED', 'REJECTED', 'PARTIALLY_FOLLOWED', 'ACKNOWLEDGED'
    ));

ALTER TABLE session_events
    DROP CONSTRAINT session_events_type_check;

ALTER TABLE session_events
    ADD CONSTRAINT session_events_type_check CHECK (type IN (
        'SESSION_STARTED', 'GPS_UPDATED', 'GPS_ACCURACY_DEGRADED', 'WEATHER_UPDATED',
        'WAYPOINT_ENTERED', 'WAYPOINT_LEFT', 'LURE_CHANGED', 'DEPTH_CHANGED',
        'RETRIEVE_CHANGED', 'ADVICE_CREATED', 'ADVICE_ACCEPTED', 'ADVICE_REJECTED',
        'ADVICE_ACKNOWLEDGED', 'USER_MOVED', 'BITE', 'FISH_ON', 'CATCH_CREATED',
        'NO_BITE', 'PLAN_REPLANNED', 'SAFETY_ALERT', 'SESSION_PAUSED',
        'SESSION_RESUMED', 'SESSION_COMPLETED', 'USER_STARTED_AD_HOC_FISHING',
        'USER_ENDED_AD_HOC_FISHING'
    ));

ALTER TABLE fishing_sessions
    DROP CONSTRAINT fishing_sessions_activity_state_source_check;

ALTER TABLE fishing_sessions
    ADD CONSTRAINT fishing_sessions_activity_state_source_check
        CHECK (activity_state_source IN (
            'OVERRIDE', 'SESSION_PAUSE', 'EFFORT_SEGMENT', 'PROGRESS', 'INFERRED',
            'USER_AD_HOC', 'UNKNOWN'
        ));

ALTER TABLE guidance_trigger_outbox
    DROP CONSTRAINT guidance_trigger_outbox_primary_trigger_check;

ALTER TABLE guidance_trigger_outbox
    ADD CONSTRAINT guidance_trigger_outbox_primary_trigger_check CHECK (primary_trigger IN (
        'NO_BITE_THRESHOLD', 'FISH_ON', 'REPEATED_BITE_PATTERN',
        'WAYPOINT_REACHED', 'PLAN_STEP_COMPLETED', 'SIGNIFICANT_WEATHER_CHANGE',
        'SIGNIFICANT_LOCATION_CHANGE', 'CONSECUTIVE_FAILURE', 'SAFETY_STATE_CHANGED',
        'ROUTE_DEVIATION', 'RETURN_RISK_CHANGED',
        'USER_STARTED_AD_HOC_FISHING', 'USER_ENDED_AD_HOC_FISHING'
    ));

ALTER TABLE agent_runs
    DROP CONSTRAINT agent_runs_trigger_check;

ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_trigger_check CHECK (trigger IN (
        'USER_REQUEST', 'NO_BITE_THRESHOLD', 'FISH_ON', 'REPEATED_BITE_PATTERN',
        'WAYPOINT_REACHED', 'PLAN_STEP_COMPLETED', 'SIGNIFICANT_WEATHER_CHANGE',
        'SIGNIFICANT_LOCATION_CHANGE', 'CONSECUTIVE_FAILURE', 'SAFETY_STATE_CHANGED',
        'ROUTE_DEVIATION', 'RETURN_RISK_CHANGED',
        'USER_STARTED_AD_HOC_FISHING', 'USER_ENDED_AD_HOC_FISHING'
    ));

CREATE TABLE session_ad_hoc_fishing_stops (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    client_event_id VARCHAR(128) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    location GEOMETRY(Point, 4326),
    gps_accuracy_m NUMERIC(8, 2),
    lake_feature_id UUID,
    fishing_target_id UUID,
    zone_id UUID,
    subtarget_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_ad_hoc_fishing_stops_session_client_key
        UNIQUE (fishing_session_id, client_event_id)
);

CREATE UNIQUE INDEX idx_session_ad_hoc_fishing_stops_one_open
    ON session_ad_hoc_fishing_stops (fishing_session_id)
    WHERE ended_at IS NULL;

CREATE INDEX idx_session_ad_hoc_fishing_stops_session
    ON session_ad_hoc_fishing_stops (fishing_session_id, started_at);

ALTER TABLE catch_events
    ADD COLUMN ad_hoc_fishing_stop_id UUID REFERENCES session_ad_hoc_fishing_stops (id);

CREATE INDEX idx_catch_events_ad_hoc_stop
    ON catch_events (ad_hoc_fishing_stop_id)
    WHERE ad_hoc_fishing_stop_id IS NOT NULL;
