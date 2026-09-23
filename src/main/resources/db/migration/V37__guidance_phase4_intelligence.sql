-- Phase 4 intelligence storage. Grain, geography, and follow semantics are frozen here.
-- Do not change unique keys after this migration. Do not use UUID/empty-string sentinels
-- for unknown dimensions: NULL means unknown/not applicable, and NULLS NOT DISTINCT
-- treats two NULLs as the same key. EmpiricalAlgorithm.VERSION starts at 1.

-- Follow state on user_action_events is dimension-local. followed_primary remains the
-- derived "was the primary recommendation executed" flag.
ALTER TABLE user_action_events
    ADD COLUMN followed_recommendation BOOLEAN,
    ADD COLUMN recommendation_role VARCHAR(16);

ALTER TABLE user_action_events
    ADD CONSTRAINT user_action_events_recommendation_role_check CHECK (
        recommendation_role IS NULL OR recommendation_role IN ('PRIMARY', 'SECONDARY')
    );

UPDATE agent_feedback
SET reject_reason = 'UNSPECIFIED'
WHERE reject_reason IS NOT NULL
  AND reject_reason NOT IN (
      'TOO_FAR', 'TOO_ROUGH', 'WANT_TO_STAY', 'DO_NOT_WANT_LURE_CHANGE', 'OTHER', 'UNSPECIFIED'
  );

ALTER TABLE agent_feedback
    ADD CONSTRAINT agent_feedback_reject_reason_check CHECK (
        reject_reason IS NULL OR reject_reason IN (
            'TOO_FAR', 'TOO_ROUGH', 'WANT_TO_STAY', 'DO_NOT_WANT_LURE_CHANGE', 'OTHER', 'UNSPECIFIED'
        )
    );

CREATE TABLE historical_performance_contributions (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    lake_id UUID REFERENCES lakes (id),
    zone_id UUID REFERENCES lake_fishing_zones (id),
    trip_waypoint_id UUID REFERENCES trip_waypoints (id),
    species VARCHAR(32),
    season_bucket VARCHAR(16),
    time_bucket VARCHAR(16),
    structure VARCHAR(32),
    wind_bucket VARCHAR(16),
    wind_direction_bucket VARCHAR(16),
    lure_family VARCHAR(32),
    empirical_algorithm_version INTEGER NOT NULL,
    fishing_effort_seconds BIGINT NOT NULL DEFAULT 0,
    bite_count INTEGER NOT NULL DEFAULT 0,
    fish_on_count INTEGER NOT NULL DEFAULT 0,
    landed_count INTEGER NOT NULL DEFAULT 0,
    contributing_session_waypoint_count INTEGER NOT NULL DEFAULT 0,
    source_hash VARCHAR(128),
    rebuilt_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT historical_perf_contrib_species_check CHECK (
        species IS NULL OR species IN (
            'SMALLMOUTH_BASS', 'LARGEMOUTH_BASS', 'WALLEYE', 'NORTHERN_PIKE', 'MUSKELLUNGE',
            'LAKE_TROUT', 'RAINBOW_TROUT', 'BROOK_TROUT', 'YELLOW_PERCH', 'CRAPPIE', 'PANFISH', 'OTHER'
        )
    ),
    CONSTRAINT historical_perf_contrib_season_check CHECK (
        season_bucket IS NULL OR season_bucket IN ('SPRING', 'SUMMER', 'FALL', 'WINTER')
    ),
    CONSTRAINT historical_perf_contrib_time_check CHECK (
        time_bucket IS NULL OR time_bucket IN ('DAWN', 'MORNING', 'MIDDAY', 'AFTERNOON', 'DUSK', 'NIGHT')
    ),
    CONSTRAINT historical_perf_contrib_structure_check CHECK (
        structure IS NULL OR structure IN ('HUMP', 'DROP_OFF', 'FLAT', 'POINT', 'BASIN', 'ISLAND_EDGE')
    ),
    CONSTRAINT historical_perf_contrib_wind_check CHECK (
        wind_bucket IS NULL OR wind_bucket IN ('CALM', 'MODERATE', 'STRONG')
    ),
    CONSTRAINT historical_perf_contrib_wind_dir_check CHECK (
        wind_direction_bucket IS NULL OR wind_direction_bucket IN (
            'N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW', 'VARIABLE'
        )
    ),
    CONSTRAINT historical_perf_contrib_lure_check CHECK (
        lure_family IS NULL OR lure_family IN (
            'PADDLETAIL', 'MINNOW_SOFT_PLASTIC', 'GRUB', 'TUBE', 'NED_RIG', 'DROP_SHOT_BAIT',
            'JIG', 'JERKBAIT', 'CRANKBAIT', 'LIPLESS_CRANKBAIT', 'SPINNERBAIT', 'CHATTERBAIT',
            'SPOON', 'INLINE_SPINNER', 'TOPWATER', 'FROG', 'TEXAS_RIG', 'CAROLINA_RIG', 'BUZZBAIT', 'OTHER'
        )
    ),
    CONSTRAINT historical_perf_contrib_version_check CHECK (empirical_algorithm_version >= 1),
    CONSTRAINT historical_perf_contrib_grain_key UNIQUE NULLS NOT DISTINCT (
        fishing_session_id, lake_id, zone_id, trip_waypoint_id, species, season_bucket, time_bucket,
        structure, wind_bucket, wind_direction_bucket, lure_family, empirical_algorithm_version
    )
);

CREATE INDEX idx_historical_perf_contrib_session
    ON historical_performance_contributions (fishing_session_id);

CREATE TABLE historical_performance (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    lake_id UUID REFERENCES lakes (id),
    zone_id UUID REFERENCES lake_fishing_zones (id),
    trip_waypoint_id UUID REFERENCES trip_waypoints (id),
    species VARCHAR(32),
    season_bucket VARCHAR(16),
    time_bucket VARCHAR(16),
    structure VARCHAR(32),
    wind_bucket VARCHAR(16),
    wind_direction_bucket VARCHAR(16),
    lure_family VARCHAR(32),
    empirical_algorithm_version INTEGER NOT NULL,
    fishing_effort_seconds BIGINT NOT NULL DEFAULT 0,
    bite_count INTEGER NOT NULL DEFAULT 0,
    fish_on_count INTEGER NOT NULL DEFAULT 0,
    landed_count INTEGER NOT NULL DEFAULT 0,
    contributing_session_waypoint_count INTEGER NOT NULL DEFAULT 0,
    effort_minutes NUMERIC(12, 3),
    bite_rate NUMERIC(12, 6),
    fish_on_rate NUMERIC(12, 6),
    landing_rate NUMERIC(12, 6),
    cpue NUMERIC(12, 6),
    smoothed_score NUMERIC(8, 5),
    sample_confidence NUMERIC(4, 3),
    updated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT historical_performance_species_check CHECK (
        species IS NULL OR species IN (
            'SMALLMOUTH_BASS', 'LARGEMOUTH_BASS', 'WALLEYE', 'NORTHERN_PIKE', 'MUSKELLUNGE',
            'LAKE_TROUT', 'RAINBOW_TROUT', 'BROOK_TROUT', 'YELLOW_PERCH', 'CRAPPIE', 'PANFISH', 'OTHER'
        )
    ),
    CONSTRAINT historical_performance_season_check CHECK (
        season_bucket IS NULL OR season_bucket IN ('SPRING', 'SUMMER', 'FALL', 'WINTER')
    ),
    CONSTRAINT historical_performance_time_check CHECK (
        time_bucket IS NULL OR time_bucket IN ('DAWN', 'MORNING', 'MIDDAY', 'AFTERNOON', 'DUSK', 'NIGHT')
    ),
    CONSTRAINT historical_performance_structure_check CHECK (
        structure IS NULL OR structure IN ('HUMP', 'DROP_OFF', 'FLAT', 'POINT', 'BASIN', 'ISLAND_EDGE')
    ),
    CONSTRAINT historical_performance_wind_check CHECK (
        wind_bucket IS NULL OR wind_bucket IN ('CALM', 'MODERATE', 'STRONG')
    ),
    CONSTRAINT historical_performance_wind_dir_check CHECK (
        wind_direction_bucket IS NULL OR wind_direction_bucket IN (
            'N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW', 'VARIABLE'
        )
    ),
    CONSTRAINT historical_performance_lure_check CHECK (
        lure_family IS NULL OR lure_family IN (
            'PADDLETAIL', 'MINNOW_SOFT_PLASTIC', 'GRUB', 'TUBE', 'NED_RIG', 'DROP_SHOT_BAIT',
            'JIG', 'JERKBAIT', 'CRANKBAIT', 'LIPLESS_CRANKBAIT', 'SPINNERBAIT', 'CHATTERBAIT',
            'SPOON', 'INLINE_SPINNER', 'TOPWATER', 'FROG', 'TEXAS_RIG', 'CAROLINA_RIG', 'BUZZBAIT', 'OTHER'
        )
    ),
    CONSTRAINT historical_performance_version_check CHECK (empirical_algorithm_version >= 1),
    CONSTRAINT historical_performance_grain_key UNIQUE NULLS NOT DISTINCT (
        lake_id, zone_id, trip_waypoint_id, species, season_bucket, time_bucket,
        structure, wind_bucket, wind_direction_bucket, lure_family, empirical_algorithm_version
    )
);

CREATE TABLE user_fishing_preferences (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    schema_version VARCHAR(64) NOT NULL,
    avoid_long_move_in_wind BOOLEAN,
    preferred_techniques JSONB NOT NULL DEFAULT '[]'::jsonb,
    disliked_techniques JSONB NOT NULL DEFAULT '[]'::jsonb,
    max_move_meters NUMERIC(12, 2),
    wind_conservatism NUMERIC(4, 3),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inferred_user_preferences (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id),
    key VARCHAR(128) NOT NULL,
    value VARCHAR(512) NOT NULL,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    confidence NUMERIC(4, 3) NOT NULL,
    first_observed_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT inferred_user_preferences_user_key UNIQUE (user_id, key),
    CONSTRAINT inferred_user_preferences_evidence_check CHECK (evidence_count >= 0)
);

CREATE TABLE semantic_memories (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES users (id),
    kind VARCHAR(32) NOT NULL,
    text TEXT NOT NULL,
    embedding_ref VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT semantic_memories_kind_check CHECK (kind IN (
        'USER_PREFERENCE_NOTE', 'SESSION_INSIGHT', 'FREE_TEXT_FEEDBACK', 'STYLE_NOTE'
    ))
);

CREATE INDEX idx_semantic_memories_user ON semantic_memories (user_id, created_at DESC);

CREATE TABLE session_summaries (
    fishing_session_id UUID PRIMARY KEY REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    summary_text TEXT NOT NULL,
    key_facts JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_reflections (
    id UUID PRIMARY KEY,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    schema_version VARCHAR(64) NOT NULL,
    run_id UUID REFERENCES agent_runs (id),
    claim_kind VARCHAR(32) NOT NULL,
    cause_kind VARCHAR(32),
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_reflections_claim_check CHECK (claim_kind IN (
        'OBSERVED_FACT', 'SESSION_HYPOTHESIS', 'USER_PREFERENCE', 'UNCERTAIN_INFERENCE'
    )),
    CONSTRAINT agent_reflections_cause_check CHECK (
        cause_kind IS NULL OR cause_kind IN (
            'STRATEGY_FAILURE', 'USER_PREFERENCE_CONFLICT', 'EVIDENCE_CONTRADICTION'
        )
    )
);

CREATE INDEX idx_agent_reflections_session ON agent_reflections (fishing_session_id, created_at);

CREATE TABLE outcome_attributions (
    id UUID PRIMARY KEY,
    schema_version VARCHAR(64) NOT NULL,
    fishing_session_id UUID NOT NULL REFERENCES fishing_sessions (id),
    outcome_event_id UUID NOT NULL,
    fish_interaction_id UUID,
    delivered_decision_id UUID REFERENCES agent_delivered_decisions (id),
    attribution_dimension VARCHAR(16) NOT NULL,
    followed_recommendation BOOLEAN NOT NULL,
    recommendation_role VARCHAR(16) NOT NULL,
    outcome_kind VARCHAR(16) NOT NULL,
    window_kind VARCHAR(16),
    confidence NUMERIC(4, 3),
    attributed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT outcome_attributions_dimension_check CHECK (
        attribution_dimension IN ('LOCATION', 'LURE', 'DEPTH', 'RETRIEVE')
    ),
    CONSTRAINT outcome_attributions_role_check CHECK (
        recommendation_role IN ('PRIMARY', 'SECONDARY')
    ),
    CONSTRAINT outcome_attributions_kind_check CHECK (
        outcome_kind IN ('BITE', 'FISH_ON', 'CATCH_LANDED', 'CATCH_LOST', 'NO_BITE', 'NONE')
    ),
    CONSTRAINT outcome_attributions_window_check CHECK (
        window_kind IS NULL OR window_kind IN ('LURE', 'RETRIEVE', 'DEPTH', 'MOVE', 'STAY')
    )
);

-- Same fish + same decision + same dimension + same role is one attribution (upgrade FISH_ON → CATCH_*).
CREATE UNIQUE INDEX uq_outcome_attributions_fish_dimension_role
    ON outcome_attributions (
        delivered_decision_id, attribution_dimension, recommendation_role, fish_interaction_id
    )
    WHERE fish_interaction_id IS NOT NULL;

CREATE INDEX idx_outcome_attributions_session
    ON outcome_attributions (fishing_session_id, attributed_at);

CREATE TABLE session_live_position (
    fishing_session_id UUID PRIMARY KEY REFERENCES fishing_sessions (id),
    location GEOGRAPHY(Point, 4326) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    accuracy_m DOUBLE PRECISION,
    activity_state VARCHAR(16) NOT NULL,
    lake_id UUID REFERENCES lakes (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_live_position_activity_check CHECK (
        activity_state IN ('TRANSIT', 'FISHING', 'PAUSED', 'UNKNOWN')
    )
);

CREATE INDEX idx_session_live_position_location
    ON session_live_position USING GIST (location);

CREATE INDEX idx_session_live_position_activity_recorded
    ON session_live_position (activity_state, recorded_at);

-- Source-transaction enqueue record. Phase 6 publishes these rows to SQS; do not drop this table.
CREATE TABLE guidance_learning_outbox (
    id UUID PRIMARY KEY,
    fishing_session_id UUID REFERENCES fishing_sessions (id),
    job_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 8,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at TIMESTAMPTZ,
    claim_token UUID,
    last_error VARCHAR(1000),
    published_at TIMESTAMPTZ,
    CONSTRAINT guidance_learning_outbox_job_type_check CHECK (job_type IN (
        'ATTRIBUTE_OUTCOME', 'AGGREGATE_EMPIRICAL', 'SESSION_SUMMARY',
        'REFLECTION_EVAL', 'PREFERENCE_UPDATE'
    )),
    CONSTRAINT guidance_learning_outbox_status_check CHECK (status IN (
        'PENDING', 'CLAIMED', 'DONE', 'FAILED', 'DLQ'
    )),
    CONSTRAINT guidance_learning_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT guidance_learning_outbox_claim_token_key UNIQUE (claim_token)
);

CREATE INDEX idx_guidance_learning_outbox_claimable
    ON guidance_learning_outbox (status, available_at)
    WHERE status IN ('PENDING', 'CLAIMED');

CREATE INDEX idx_guidance_learning_outbox_session
    ON guidance_learning_outbox (fishing_session_id, created_at);
