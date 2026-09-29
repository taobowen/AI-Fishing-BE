-- Derived spatial shortlists for template POINT/PATH and required points.
-- Source geometry stays on fishing_template_targets and trip_required_points.

CREATE TABLE intent_spatial_resolutions (
    id UUID PRIMARY KEY,
    origin_kind VARCHAR(32) NOT NULL,
    origin_template_target_id UUID REFERENCES fishing_template_targets (id) ON DELETE CASCADE,
    origin_required_point_id UUID REFERENCES trip_required_points (id) ON DELETE CASCADE,
    spatial_snapshot_id UUID NOT NULL,
    matching_version VARCHAR(64) NOT NULL,
    template_point_radius_m DOUBLE PRECISION NOT NULL,
    template_path_corridor_m DOUBLE PRECISION NOT NULL,
    required_point_radius_m DOUBLE PRECISION NOT NULL,
    max_matches INT NOT NULL,
    rank INT NOT NULL,
    fishing_target_id UUID,
    feature_id UUID,
    distance_m DOUBLE PRECISION,
    overlap_m DOUBLE PRECISION,
    synthetic_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT intent_spatial_resolutions_kind_chk CHECK (origin_kind IN ('TEMPLATE_TARGET', 'REQUIRED_POINT')),
    CONSTRAINT intent_spatial_resolutions_origin_chk CHECK (
        (origin_kind = 'TEMPLATE_TARGET' AND origin_template_target_id IS NOT NULL AND origin_required_point_id IS NULL)
        OR
        (origin_kind = 'REQUIRED_POINT' AND origin_required_point_id IS NOT NULL AND origin_template_target_id IS NULL)
    ),
    CONSTRAINT intent_spatial_resolutions_rank_chk CHECK (rank >= 1 AND rank <= max_matches)
);

CREATE UNIQUE INDEX uq_intent_spatial_resolutions_template
    ON intent_spatial_resolutions (
        origin_template_target_id,
        spatial_snapshot_id,
        matching_version,
        template_point_radius_m,
        template_path_corridor_m,
        required_point_radius_m,
        max_matches,
        rank
    )
    WHERE origin_template_target_id IS NOT NULL;

CREATE UNIQUE INDEX uq_intent_spatial_resolutions_required
    ON intent_spatial_resolutions (
        origin_required_point_id,
        spatial_snapshot_id,
        matching_version,
        template_point_radius_m,
        template_path_corridor_m,
        required_point_radius_m,
        max_matches,
        rank
    )
    WHERE origin_required_point_id IS NOT NULL;

CREATE INDEX idx_intent_spatial_resolutions_snapshot
    ON intent_spatial_resolutions (spatial_snapshot_id);

-- Immutable copy for a planning run. Origin ids are plain values so deleting the live
-- template target or required pin does not erase historical explanation.
CREATE TABLE trip_planning_intent_resolutions (
    id UUID PRIMARY KEY,
    planning_input_target_id UUID NOT NULL REFERENCES trip_planning_input_targets (id) ON DELETE CASCADE,
    origin_kind VARCHAR(32) NOT NULL,
    origin_template_target_id UUID,
    origin_required_point_id UUID,
    spatial_snapshot_id UUID NOT NULL,
    matching_version VARCHAR(64) NOT NULL,
    template_point_radius_m DOUBLE PRECISION NOT NULL,
    template_path_corridor_m DOUBLE PRECISION NOT NULL,
    required_point_radius_m DOUBLE PRECISION NOT NULL,
    max_matches INT NOT NULL,
    rank INT NOT NULL,
    fishing_target_id UUID,
    feature_id UUID,
    distance_m DOUBLE PRECISION,
    overlap_m DOUBLE PRECISION,
    synthetic_fallback BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_planning_intent_resolutions_kind_chk CHECK (origin_kind IN ('TEMPLATE_TARGET', 'REQUIRED_POINT'))
);

CREATE INDEX idx_trip_planning_intent_resolutions_target
    ON trip_planning_intent_resolutions (planning_input_target_id, rank);
