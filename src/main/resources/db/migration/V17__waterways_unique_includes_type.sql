-- Shoreline, island, and watercourse rows share lake_waterways. OHN OGF_IDs can
-- collide across those layers, and a single layer can repeat the same OGF_ID.
DROP INDEX IF EXISTS idx_lake_waterways_src;
CREATE UNIQUE INDEX idx_lake_waterways_src
    ON lake_waterways (lake_id, provider, type, source_record_id, import_version)
    WHERE source_record_id IS NOT NULL;
