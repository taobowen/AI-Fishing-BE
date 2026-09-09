ALTER TABLE spatial_planning_snapshots
    ADD COLUMN IF NOT EXISTS navigation_grid JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE lake_navigation_tiles (
    id UUID PRIMARY KEY,
    spatial_planning_snapshot_id UUID NOT NULL REFERENCES spatial_planning_snapshots (id) ON DELETE CASCADE,
    tile_x INTEGER NOT NULL,
    tile_y INTEGER NOT NULL,
    traversability_mask BYTEA NOT NULL,
    clearance_m BYTEA,
    CONSTRAINT lake_navigation_tiles_cell UNIQUE (spatial_planning_snapshot_id, tile_x, tile_y)
);

CREATE INDEX idx_lake_navigation_tiles_snapshot
    ON lake_navigation_tiles (spatial_planning_snapshot_id);

ALTER TABLE lake_fishing_water_paths
    ADD COLUMN IF NOT EXISTS navigation_version VARCHAR(64);

-- Do not drop lake_fishing_nav_nodes / lake_fishing_nav_edges while any READY
-- snapshot still uses water-nav-v2 (or any version that reads those tables).
DO $$
DECLARE
    ready_v2 BIGINT;
BEGIN
    SELECT COUNT(*) INTO ready_v2
    FROM spatial_planning_snapshots
    WHERE status = 'READY'
      AND navigation_version IN ('water-nav-v2', 'water-nav-v1');
    IF ready_v2 > 0 THEN
        RAISE NOTICE 'Keeping nav node/edge tables: % READY v2 snapshots remain', ready_v2;
    ELSE
        RAISE NOTICE 'No READY water-nav-v2 snapshots; nav node/edge tables kept read-only for cleanup later';
    END IF;
END $$;
