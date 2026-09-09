-- CASCADE from snapshot/nodes must not seq-scan 1.8M edges per node.
CREATE INDEX IF NOT EXISTS idx_lake_fishing_nav_edges_snapshot
    ON lake_fishing_nav_edges (spatial_planning_snapshot_id);
CREATE INDEX IF NOT EXISTS idx_lake_fishing_nav_edges_from_node
    ON lake_fishing_nav_edges (from_node_id);
CREATE INDEX IF NOT EXISTS idx_lake_fishing_nav_edges_to_node
    ON lake_fishing_nav_edges (to_node_id);
CREATE INDEX IF NOT EXISTS idx_lake_fishing_nav_nodes_snapshot
    ON lake_fishing_nav_nodes (spatial_planning_snapshot_id);
