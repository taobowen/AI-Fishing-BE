-- Private fishing templates, trip planning modes, required points, and generate-time input snapshots.

CREATE TABLE fishing_templates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    lake_id UUID NOT NULL REFERENCES lakes (id),
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fishing_templates_user_lake ON fishing_templates (user_id, lake_id);
CREATE INDEX idx_fishing_templates_lake_id ON fishing_templates (lake_id);

CREATE TABLE fishing_template_targets (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES fishing_templates (id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    name VARCHAR(128),
    geometry GEOMETRY(Geometry, 4326) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fishing_template_targets_kind_chk CHECK (kind IN ('POINT', 'PATH', 'ZONE'))
);

CREATE INDEX idx_fishing_template_targets_template ON fishing_template_targets (template_id, sort_order);
CREATE INDEX idx_fishing_template_targets_geometry ON fishing_template_targets USING GIST (geometry);

ALTER TABLE trips
    ADD COLUMN planning_mode VARCHAR(16) NOT NULL DEFAULT 'AI',
    ADD COLUMN fishing_template_id UUID REFERENCES fishing_templates (id) ON DELETE SET NULL;

ALTER TABLE trips
    ADD CONSTRAINT trips_planning_mode_chk CHECK (planning_mode IN ('AI', 'HYBRID', 'CUSTOM'));

CREATE INDEX idx_trips_fishing_template_id ON trips (fishing_template_id);

CREATE TABLE trip_required_points (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL REFERENCES trips (id) ON DELETE CASCADE,
    location GEOMETRY(Point, 4326) NOT NULL,
    label VARCHAR(128),
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_trip_required_points_trip ON trip_required_points (trip_id, sort_order);
CREATE INDEX idx_trip_required_points_location ON trip_required_points USING GIST (location);

CREATE TABLE trip_planning_input_snapshots (
    id UUID PRIMARY KEY,
    planning_run_id UUID NOT NULL UNIQUE REFERENCES trip_planning_runs (id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL,
    template_id UUID REFERENCES fishing_templates (id) ON DELETE SET NULL,
    template_name VARCHAR(128),
    template_target_count INT NOT NULL DEFAULT 0,
    required_point_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT trip_planning_input_snapshots_mode_chk CHECK (mode IN ('AI', 'HYBRID', 'CUSTOM'))
);

CREATE TABLE trip_planning_input_targets (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES trip_planning_input_snapshots (id) ON DELETE CASCADE,
    source VARCHAR(32) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    name VARCHAR(128),
    geometry GEOMETRY(Geometry, 4326) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    origin_template_target_id UUID REFERENCES fishing_template_targets (id) ON DELETE SET NULL,
    origin_required_point_id UUID REFERENCES trip_required_points (id) ON DELETE SET NULL,
    CONSTRAINT trip_planning_input_targets_source_chk CHECK (source IN ('TEMPLATE', 'REQUIRED_POINT')),
    CONSTRAINT trip_planning_input_targets_kind_chk CHECK (kind IN ('POINT', 'PATH', 'ZONE'))
);

CREATE INDEX idx_trip_planning_input_targets_snapshot
    ON trip_planning_input_targets (snapshot_id, sort_order);
CREATE INDEX idx_trip_planning_input_targets_geometry
    ON trip_planning_input_targets USING GIST (geometry);

ALTER TABLE trip_waypoints
    ADD COLUMN candidate_source VARCHAR(16) NOT NULL DEFAULT 'AI';

ALTER TABLE trip_waypoints
    ADD CONSTRAINT trip_waypoints_candidate_source_chk
        CHECK (candidate_source IN ('REQUIRED', 'TEMPLATE', 'AI'));
