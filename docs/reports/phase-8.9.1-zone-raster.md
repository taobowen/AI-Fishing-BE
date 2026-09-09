# Phase 8.9.1 follow-up: Zone quality + nav raster

Date: 2026-09-05. Planning algorithm **1.4.0** (not bumped). Flyway **V26**. Product versions: `spatial-targets-v2`, **`physical-zones-v3`**, **`water-nav-v3`**.

Lake: Rice Lake (`44444444-4444-4444-4444-444444444445`), GIS analysis `8c32813a-11b7-4a8f-8712-6586b697e5f0` (229 features). Snapshot `1fa26376-3ba4-43ad-b6a8-5b286fb67738`.

## Before / after

BEFORE is the last aborted `water-nav-v2` GIS build (one giant TX, per-zone 25 m node/edge persist). AFTER is the first READY `water-nav-v3` rebuild on the same feature snapshot.

| Metric | BEFORE (v2, aborted) | AFTER (v3, READY) |
| --- | ---: | ---: |
| GIS features | 229 | 229 |
| Targets | 3824 | 3824 |
| Physical zones | 345 (partial / not persisted) | 1 |
| Zone members (largest) | n/a | 3818 |
| Theoretical pairs `n(n-1)/2` | ~7.3e6 | 7,309,576 |
| Neighbor pairs (STRtree) | n/a (all-pairs) | 396,448 |
| Expensive connectivity checks | n/a | 396,448 |
| A* connectivity checks | n/a | 67,210 |
| Navigable raster cells | ~245k nodes (per-zone overlap) | 147,771 |
| Raster tiles / mask+clearance bytes | n/a | 24 / 49,152 + 393,216 |
| **Nav edge rows** | ~1.81M (attempted; rolled back to 0) | **0** |
| Water paths (precomputed) | n/a | 2 |
| Cluster time | ~12.3 min | **115.4 s** |
| Target persist / enrich | (inside 6 h TX) | 55.9 s |
| Raster build + tile persist | n/a | 3.8 s (`WATER_PATH_BUILD` raster stage) |
| Persist / total | multi-hour, curl timeout, TX rollback | **3 min 02 s** READY |

Neighbor checks are **5.4%** of n² (396k vs 7.3M). Admin POST returned `RUNNING` in <1 s; the job ran on `spatial-snap-1`, not the request thread.

`VACUUM ANALYZE` / `REINDEX` were applied to leftover `lake_fishing_nav_edges` / `lake_fishing_nav_nodes` heaps. V26 kept those tables: no READY `water-nav-v2` row existed.

## Quality notes

**Mixed types.** Hard `compatible()` is gone. Island-edge, drop-off, hump, and flat may share a zone when they sit in one compact water pocket (unit tests A/B). Feature type is label / scoring / subplan order only.

**Barrier splits.** Opposite sides of an island with a long water detour do not merge (unit test C). Pair link uses water-path on the shared raster, not a 220 m geodesic cutoff. `clusterConnectM` is not membership.

**Chain giants.** The first Rice READY snapshot is **one lake-wide zone** (92.3 km² polygon, 45 rings = shoreline + 44 island holes, 3818 members). Root cause: adjacent PATH segments share endpoints, so MST join lengths are ~0 m and the unused 900 m `clusterMaxDiameterM` never fired. A bbox-span diameter check is now in `FishingZoneBuilder` (max of MST join length and member-geometry bbox metres). **Delete this READY row and rebuild** to pick up that split. Goal remains coherent local worlds, not minimum zone count.

**Island holes.** Envelope still unions members → concave hull → ∩ water → − islands. The Rice zone is a valid `ST_Polygon` with 44 interior rings. Unit tests E/G keep holes and keep membership independent of envelope overlap.

**STRtree on target envelopes.** Unit test K2: two long PATHs whose entries are far but envelopes are close are discovered. Rice neighbor count ≪ theoretical pairs (test N).

**Raster / A\*.** Conservative 3×3 water-coverage cells (not center-in-water). 147,771 navigable 25 m cells, 24 tiles, implicit 8-neighbor A\*, no diagonal corner cut (tests I/I2). Overlapping windows reuse the same cells (test J). v3 wrote **zero** `lake_fishing_nav_edges` (test H).

**WaterPath cache.** Append-only. Post-READY lazy append does not change snapshot id/status (tests G/K). Precompute is portal-scale only (2 paths on this lake-wide zone).

**Lifecycle.** Multi-TX stages `STARTED` → `RASTER` → `TARGETS` → `ZONES` → `PATHS` → `READY`. Generate still rejects non-READY (tests L/M).

## Coverage density (segmentation-stable)

Not `targetCount / area`. During cluster growth:

- POINT weight = 1
- PATH weight = max(1, useful length m / `pathCoverageUnitM`) with unit **80 m**
- Density = Σ weights / Σ member footprint area (km²)
- Reject if density < `zoneMinCoverageDensity` (6 / km²) or bbox stretch `diameter / sqrt(area)` > 8

PATH segmentation that adds rows without changing the lake does not by itself change density.

## Sample ZoneSubPlanner sequence

Generate was not re-run on this READY snapshot in-session (one lake-wide zone is not a useful VisitScope fixture). After the bbox-diameter rebuild, a typical island-edge + drop-off pocket should subplan as: entry portal → nearest high-weight member PATH/POINT (8–20 min dwell) → next member on the shared raster → exit portal. Internal hops use `SnapshotWaterPathService` on the same tiles.

## Ops

1. Flyway V26 is applied (`navigation_grid` + `lake_navigation_tiles`).
2. To rebuild Rice after the bbox-diameter fix: delete the READY v3 row for this key (or it will be reused), then `POST /api/v1/admin/lakes/{riceId}/spatial-snapshots?pipeline=GIS`.
3. GET the same path for `counts.stage` / pair stats while `RUNNING`.
4. Do not drop `lake_fishing_nav_nodes` / `nav_edges` until no lake has a READY `water-nav-v2` snapshot.
