CREATE TABLE spatial_planning_snapshots (
    id UUID PRIMARY KEY,
    lake_id UUID NOT NULL REFERENCES lakes (id),
    feature_pipeline VARCHAR(32) NOT NULL,
    feature_analysis_version VARCHAR(128) NOT NULL,
    target_derivation_version VARCHAR(64) NOT NULL,
    zone_builder_version VARCHAR(64) NOT NULL,
    navigation_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    timings JSONB NOT NULL DEFAULT '{}'::jsonb,
    counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT spatial_planning_snapshots_status_chk CHECK (status IN ('RUNNING', 'READY', 'FAILED')),
    CONSTRAINT spatial_planning_snapshots_key_uidx UNIQUE (
        lake_id,
        feature_pipeline,
        feature_analysis_version,
        target_derivation_version,
        zone_builder_version,
        navigation_version
    )
);

CREATE INDEX idx_spatial_planning_snapshots_lake
    ON spatial_planning_snapshots (lake_id, feature_pipeline, status);

ALTER TABLE lake_fishing_targets
    ADD COLUMN spatial_planning_snapshot_id UUID REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    ADD COLUMN closed_loop BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN path_topology VARCHAR(32),
    ADD COLUMN chainage_start_m NUMERIC(12, 2),
    ADD COLUMN chainage_end_m NUMERIC(12, 2),
    ADD COLUMN split_reason VARCHAR(64),
    ADD COLUMN static_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN min_depth_m NUMERIC(8, 2),
    ADD COLUMN max_depth_m NUMERIC(8, 2),
    ADD COLUMN representative_depth_m NUMERIC(8, 2),
    ADD COLUMN confidence NUMERIC(6, 4);

ALTER TABLE lake_fishing_targets DROP CONSTRAINT IF EXISTS lake_fishing_targets_kind_chk;
ALTER TABLE lake_fishing_targets
    ADD CONSTRAINT lake_fishing_targets_kind_chk CHECK (target_kind IN ('POINT', 'PATH', 'SEGMENT', 'AREA'));

CREATE INDEX idx_lake_fishing_targets_snapshot_id
    ON lake_fishing_targets (spatial_planning_snapshot_id);

ALTER TABLE lake_fishing_zones
    ADD COLUMN spatial_planning_snapshot_id UUID REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    ADD COLUMN navigation_version VARCHAR(64);

CREATE INDEX idx_lake_fishing_zones_snapshot_id
    ON lake_fishing_zones (spatial_planning_snapshot_id);

CREATE TABLE lake_fishing_target_samples (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    fishing_target_id UUID NOT NULL REFERENCES lake_fishing_targets (id) ON DELETE CASCADE,
    fraction NUMERIC(8, 4) NOT NULL,
    geom geometry(Point, 4326) NOT NULL,
    depth_m NUMERIC(8, 2),
    slope_deg NUMERIC(8, 2),
    aspect_deg NUMERIC(8, 2),
    orientation_deg NUMERIC(8, 2),
    static_factors JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_lake_fishing_target_samples_target
    ON lake_fishing_target_samples (fishing_target_id);
CREATE INDEX idx_lake_fishing_target_samples_geom
    ON lake_fishing_target_samples USING GIST (geom);

CREATE TABLE lake_fishing_zone_portals (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES lake_fishing_zones (id) ON DELETE CASCADE,
    portal_key VARCHAR(64) NOT NULL,
    geom geometry(Point, 4326) NOT NULL,
    sequence INTEGER NOT NULL,
    CONSTRAINT lake_fishing_zone_portals_zone_key UNIQUE (zone_id, portal_key)
);

CREATE INDEX idx_lake_fishing_zone_portals_geom
    ON lake_fishing_zone_portals USING GIST (geom);

CREATE TABLE lake_fishing_nav_nodes (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES lake_fishing_zones (id) ON DELETE CASCADE,
    cell_x INTEGER NOT NULL,
    cell_y INTEGER NOT NULL,
    geom geometry(Point, 4326) NOT NULL,
    CONSTRAINT lake_fishing_nav_nodes_cell UNIQUE (zone_id, cell_x, cell_y)
);

CREATE INDEX idx_lake_fishing_nav_nodes_zone
    ON lake_fishing_nav_nodes (zone_id);

CREATE TABLE lake_fishing_nav_edges (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES lake_fishing_zones (id) ON DELETE CASCADE,
    from_node_id UUID NOT NULL REFERENCES lake_fishing_nav_nodes (id) ON DELETE CASCADE,
    to_node_id UUID NOT NULL REFERENCES lake_fishing_nav_nodes (id) ON DELETE CASCADE,
    meters NUMERIC(12, 2) NOT NULL,
    CONSTRAINT lake_fishing_nav_edges_pair UNIQUE (zone_id, from_node_id, to_node_id)
);

CREATE INDEX idx_lake_fishing_nav_edges_zone
    ON lake_fishing_nav_edges (zone_id);

CREATE TABLE lake_fishing_water_paths (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES lake_fishing_zones (id) ON DELETE CASCADE,
    from_key VARCHAR(128) NOT NULL,
    to_key VARCHAR(128) NOT NULL,
    meters NUMERIC(12, 2) NOT NULL,
    path geometry(LineString, 4326),
    precomputed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT lake_fishing_water_paths_pair UNIQUE (spatial_planning_snapshot_id, zone_id, from_key, to_key)
);

CREATE INDEX idx_lake_fishing_water_paths_zone
    ON lake_fishing_water_paths (zone_id);

ALTER TABLE trip_waypoints
    ADD COLUMN visit_kind VARCHAR(16),
    ADD COLUMN visit_scope_id UUID,
    ADD COLUMN visit_scope_member_ids JSONB,
    ADD COLUMN visit_envelope geometry(Geometry, 4326),
    ADD COLUMN closed_loop BOOLEAN,
    ADD COLUMN traversal_key VARCHAR(64),
    ADD COLUMN spatial_planning_snapshot_id UUID REFERENCES spatial_planning_snapshots (id);

CREATE INDEX idx_trip_waypoints_visit_envelope
    ON trip_waypoints USING GIST (visit_envelope);
