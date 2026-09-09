ALTER TABLE boats
    ADD COLUMN IF NOT EXISTS manufacturer VARCHAR(255),
    ADD COLUMN IF NOT EXISTS model VARCHAR(255),
    ADD COLUMN IF NOT EXISTS year INTEGER,
    ADD COLUMN IF NOT EXISTS primary_transit_propulsion_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS motors JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS configuration_description TEXT,
    ADD COLUMN IF NOT EXISTS measured_cruise_speed_kmh NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS comfortable_round_trip_range_km NUMERIC(8, 2);

UPDATE boats
SET propulsion_types = COALESCE((
    SELECT jsonb_agg(
                   CASE elem #>> '{}'
                       WHEN 'OUTBOARD' THEN to_jsonb('GAS_OUTBOARD'::text)
                       WHEN 'TROLLING_MOTOR' THEN to_jsonb('ELECTRIC_TROLLING'::text)
                       ELSE elem
                       END
           )
    FROM jsonb_array_elements(propulsion_types) AS elem
), '[]'::jsonb)
WHERE propulsion_types IS NOT NULL;

UPDATE boats
SET primary_transit_propulsion_type = CASE
    WHEN propulsion_types @> '"GAS_OUTBOARD"'::jsonb THEN 'GAS_OUTBOARD'
    WHEN jsonb_typeof(propulsion_types) = 'array' AND jsonb_array_length(propulsion_types) > 0
        THEN propulsion_types ->> 0
    ELSE 'NONE'
    END
WHERE primary_transit_propulsion_type IS NULL;

UPDATE boats
SET motors = COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
            'propulsionType', elem #>> '{}',
            'manufacturer', NULL,
            'model', NULL,
            'horsepower', NULL,
            'thrustLb', NULL
                         ))
    FROM jsonb_array_elements(propulsion_types) AS elem
), '[]'::jsonb)
WHERE motors = '[]'::jsonb
  AND jsonb_typeof(propulsion_types) = 'array'
  AND jsonb_array_length(propulsion_types) > 0;

ALTER TABLE boats
    ALTER COLUMN primary_transit_propulsion_type SET NOT NULL;

CREATE TABLE boat_capability_profiles (
    id UUID PRIMARY KEY,
    configuration_fingerprint VARCHAR(64) NOT NULL UNIQUE,
    normalized_configuration JSONB NOT NULL,
    cruise_speed_kmh NUMERIC(6, 2),
    cruise_speed_confidence NUMERIC(4, 3),
    cruise_speed_source VARCHAR(32),
    practical_range_km NUMERIC(8, 2),
    range_confidence NUMERIC(4, 3),
    range_source VARCHAR(32),
    wind_wave_capability VARCHAR(16),
    wind_wave_confidence NUMERIC(4, 3),
    wind_wave_source VARCHAR(32),
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    resolver_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64),
    model_id VARCHAR(128),
    web_search_used BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    raw_resolution_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    resolved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_boat_capability_profiles_resolver
    ON boat_capability_profiles (resolver_version);
