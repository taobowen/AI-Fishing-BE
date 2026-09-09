CREATE TABLE lake_fishing_targets (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    feature_pipeline VARCHAR(32) NOT NULL,
    feature_analysis_version VARCHAR(128) NOT NULL,
    derivation_version VARCHAR(64) NOT NULL,
    target_kind VARCHAR(16) NOT NULL,
    semantic_type VARCHAR(64) NOT NULL,
    geometry geometry(Geometry, 4326) NOT NULL,
    representative_point geometry(Point, 4326) NOT NULL,
    fishing_corridor geometry(Geometry, 4326),
    entry_point geometry(Point, 4326),
    exit_point geometry(Point, 4326),
    selected_fishing_path geometry(LineString, 4326),
    fishing_corridor_width_m NUMERIC(8, 2),
    source_feature_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    segment_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT lake_fishing_targets_kind_chk CHECK (target_kind IN ('POINT', 'SEGMENT', 'AREA'))
);

CREATE INDEX idx_lake_fishing_targets_snapshot
    ON lake_fishing_targets (lake_id, feature_pipeline, feature_analysis_version, derivation_version);
CREATE INDEX idx_lake_fishing_targets_geom
    ON lake_fishing_targets USING GIST (geometry);
CREATE INDEX idx_lake_fishing_targets_rep
    ON lake_fishing_targets USING GIST (representative_point);

CREATE TABLE lake_fishing_zones (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    feature_pipeline VARCHAR(32) NOT NULL,
    feature_analysis_version VARCHAR(128) NOT NULL,
    builder_version VARCHAR(64) NOT NULL,
    geometry geometry(Geometry, 4326) NOT NULL,
    representative_point geometry(Point, 4326) NOT NULL,
    clustering_params JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lake_fishing_zones_snapshot
    ON lake_fishing_zones (lake_id, feature_pipeline, feature_analysis_version, builder_version);
CREATE INDEX idx_lake_fishing_zones_geom
    ON lake_fishing_zones USING GIST (geometry);

CREATE TABLE lake_fishing_zone_members (
    zone_id UUID NOT NULL REFERENCES lake_fishing_zones (id) ON DELETE CASCADE,
    fishing_target_id UUID NOT NULL REFERENCES lake_fishing_targets (id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    PRIMARY KEY (zone_id, fishing_target_id)
);

CREATE INDEX idx_lake_fishing_zone_members_target
    ON lake_fishing_zone_members (fishing_target_id);

ALTER TABLE trip_waypoints
    ADD COLUMN target_kind VARCHAR(16),
    ADD COLUMN target_geometry geometry(Geometry, 4326),
    ADD COLUMN fishing_corridor geometry(Geometry, 4326),
    ADD COLUMN entry_point geometry(Point, 4326),
    ADD COLUMN exit_point geometry(Point, 4326),
    ADD COLUMN selected_fishing_path geometry(LineString, 4326),
    ADD COLUMN fishing_corridor_width_m NUMERIC(8, 2),
    ADD COLUMN zone_id UUID,
    ADD COLUMN fishing_target_id UUID,
    ADD COLUMN planned_visit_minutes INTEGER,
    ADD COLUMN planned_fishing_minutes INTEGER,
    ADD COLUMN planned_internal_transit_minutes INTEGER,
    ADD COLUMN planned_wait_minutes INTEGER;

CREATE INDEX idx_trip_waypoints_target_geom
    ON trip_waypoints USING GIST (target_geometry);
CREATE INDEX idx_trip_waypoints_entry
    ON trip_waypoints USING GIST (entry_point);

CREATE TABLE trip_stop_subtargets (
    id UUID PRIMARY KEY,
    trip_waypoint_id UUID NOT NULL REFERENCES trip_waypoints (id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    target_kind VARCHAR(16) NOT NULL,
    fishing_target_id UUID,
    geometry geometry(Geometry, 4326),
    entry_point geometry(Point, 4326),
    exit_point geometry(Point, 4326),
    planned_arrival_at TIMESTAMPTZ,
    planned_departure_at TIMESTAMPTZ,
    planned_fishing_minutes INTEGER,
    planned_internal_transit_minutes INTEGER,
    score NUMERIC(8, 4),
    reason TEXT,
    CONSTRAINT trip_stop_subtargets_waypoint_seq UNIQUE (trip_waypoint_id, sequence)
);

ALTER TABLE catch_events
    ADD COLUMN fishing_target_id UUID,
    ADD COLUMN zone_id UUID,
    ADD COLUMN subtarget_id UUID,
    ADD COLUMN along_track_fraction NUMERIC(6, 4);

ALTER TABLE fishing_effort_segments
    ADD COLUMN fishing_target_id UUID,
    ADD COLUMN zone_id UUID;
