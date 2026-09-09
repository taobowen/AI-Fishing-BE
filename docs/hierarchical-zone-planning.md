# Hierarchical zone planning (Phase 8.9.1 + zone/raster follow-up)

Canonical **PhysicalZones** are strategy-neutral, boat-neutral clusters persisted on a `SpatialPlanningSnapshot`. They are never split per boat. Product versions: `physical-zones-v3`, `water-nav-v3`. Planning algorithm stays **1.4.0**. `clusterMaxMembers` is not a physical splitter.

Generate derives one or more connected, executable **ZoneVisitScopes** from a PhysicalZone using boat, weather, time, and water-path costs. Scopes are trip candidates, not new `lake_fishing_zones`. Macro may select multiple non-overlapping scopes from the same PhysicalZone. Persist the selected member set on the TripPlan; optional `visitEnvelope` visualizes that scope only.

## Shared zone membership (physical only)

A PhysicalZone is a **coherent local water planning world**: targets that can be fished together in one compact navigable pocket. Membership is decided on the target graph first; zone envelope geometry is built **after** membership (union → concave hull → ∩ water → − islands → sanitize). Island holes are kept. There is no polygon-overlap merge/split.

`lake_fishing_zones` membership uses:

- STRtree neighbor discovery on each target’s **actual geometry envelope**, expanded by `zoneNeighborSearchRadiusM` (~550 m). Discovery is not entry-point-only. A long PATH whose entry is far away can still be found if its geometry passes close to another PATH.
- Final pair link: same navigable water, not land-crossing, **or** a short water-path on the **shared snapshot raster**. Bbox is prune-only. `zoneWaterPathJoinMaxM` (~400 m) is the link cap. `clusterConnectM` 220 is **not** membership.
- Feature type is **not** a hard gate. Type remains for scoring, dwell, `ZoneSubPlanner` order, and `dominantType` label only. Mixed ISLAND_EDGE + DROP_OFF + HUMP (and FLAT/BASIN) may share a zone when they sit in one compact pocket.
- Coherence-constrained agglomeration (not plain connected components): grow by cheapest water-path link, then evaluate the merged cluster. Split / reject if water-path diameter (`zoneMaxWaterPathDiameterM`, default 900), max internal gap (`zoneMaxInternalGapM`), coverage density, or geometric stretch is excessive. Diameter is the max of MST join length and the member-geometry **bbox span** (PATH joints are ~0 m, so MST length alone cannot detect a shoreline chain). This kills A–B–C–D–E chain giants.
- Coverage density is **segmentation-stable**, not `targetCount / area`. Weight = 1 per POINT plus useful PATH length / `pathCoverageUnitM` (80 m). Density = Σ weights / navigable member-union area (km²). Reject if below `zoneMinCoverageDensity` (6 / km²). PATH segmentation that changes row count does not by itself change density.

Not StrategyRun, species, weather, technique, or boat capability. Never `clusterMaxMembers` as a physical splitter. Strategy-specific executable subsets are **ZoneVisitScopes** on the TripPlan.

Snapshot `counts` record `theoreticalPairCount` (`n*(n-1)/2`), `neighborPairCount` (STRtree pairs), `expensiveConnectivityChecks`, and `astarConnectivityChecks`. Neighbor checks must be ≪ n².

## Explicit entry/exit portal pairs

`ZoneSubPlanner(zone, arrivalSlot, dwell, entryPortal, exitPortal)` returns the best bounded internal subplan for that pair. No `exitHint`. The visit does not need the next macro stop.

Macro beam cursor after the visit is `exitPortal`. Next hop: `current.exitPortal → next.entryPortal`.

Cache key: full `VisitOptionKey` (`targetOrPhysicalZoneId` + `visitScopeId` + entry/exit + `traversalKey` + arrival/dwell). No Java object identity.

`ZoneSubPlanner` uses the snapshot raster / `SnapshotWaterPathService`, not a private per-call rasterization.

## Shared tiled lake raster

One snapshot-scoped **explicit metric grid** (UTM via `LocalMetricCrs`): CRS, origin X/Y, `cellSizeM`, tile width/height, full width×height. Tiles store only `tile_x` / `tile_y` against that grid. Origin is never “lon/lat or metric.”

`lake_navigation_tiles` hold a bit-packed traversability mask and an optional boat-neutral clearance (distance-to-land) byte layer. Unique `(snapshot_id, tile_x, tile_y)`.

**Build order:** lake geometry → persist RUNNING → **rasterize + persist tiles** → persist targets → **cluster using that raster** → zones/portals → bounded WaterPath precompute.

Cell traversability is **conservative**, not “center in water.” A 3×3 sample inside the cell must have water coverage ≥ `navMinWaterCoverage` (0.55) and island coverage ≤ `navMaxIslandFraction` (0.15). Thin islands are not erased; narrow land necks are not invented as channels. At 25 m, features thinner than one cell may disappear or block.

A\* is in memory on the shared raster. Adjacency is implicit (8-neighbor). **No diagonal corner cutting:** a diagonal step is allowed only if both orthogonal neighbors are traversable. No SQL during expansion. Overlapping zone windows reuse the same tile cells.

`lake_fishing_nav_nodes` / `lake_fishing_nav_edges` are not written for `water-nav-v3`. They remain in the schema until no READY `water-nav-v2` snapshot exists on any lake.

## WaterPath cache

`lake_fishing_water_paths` is an **append-only derived cache**, not immutable snapshot root content. Cache key: `snapshotId + navigationVersion + fromKey + toKey` (zone id scopes load). A miss **after READY** may append a deterministic path (`REQUIRES_NEW`) without changing snapshot id or readiness.

Immutable snapshot root: targets, samples, PhysicalZones, portals, shared navigation raster + grid metadata, algorithm/product versions.

Precompute: portal↔portal and nearby target portals only. Large zones stay lazy.

## Multi-transaction snapshot lifecycle

The build is not one multi-hour transaction.

1. Short TX: insert snapshot `RUNNING`, `stage`, initial `counts` — COMMIT.
2. Bounded TXs: tiles + raster metadata first; then targets/samples; then zones/members/portals; then optional WaterPath precompute. After each batch, update `counts` + `stage` and COMMIT. Clustering runs only after tiles are committed/readable.
3. Validate + quality gate (existing skip ratios).
4. Short TX: `status = READY` (or `FAILED`) — COMMIT.

Admin POST and `SpatialSnapshotTrigger` enqueue the build on a dedicated executor. HTTP returns the RUNNING row immediately. `build()` remains synchronous for integration tests (RUNNING commit, then finish on the caller thread).

Generate uses only `findReady` / `load` and rejects non-READY. RUNNING children are invisible. FAILED keeps child rows; cleanup is on rebuild of the same key (`deleteProducts` deletes tiles, not required nav edges). Older READY rows stay.

`counts` progress fields include `stage`, `zonesProcessed` / `zonesTotal`, `targetsBuilt`, `waterPathsBuilt`, and pair-check stats.

## Mutual exclusion

Beam `used` covers Zone members. Scheduling Zone Z forbids scheduling member A. Zone utility is the subplanner output, not the sum of members.

## Geometry robustness

After buffer / union / concave-hull / water-intersection: validate/fix, drop tiny slivers, preserve meaningful holes, normalize deterministically, reject empty/invalid Zone geometry.

## CPUE grain

Statistical resolution = `min(reliable catch resolution, reliable effort resolution)`.

Catch on a micro target with effort only at Zone grain updates **Zone** historical evidence only. Never add the same catch/effort at micro + target + Zone in one ranking pass. Phase 7 shrinkage and negative evidence stay. Display-only micro association is allowed.

## Snapshot isolation

Every static product belongs to one `spatialPlanningSnapshotId`. Unique key: `lakeId + featurePipeline + featureAnalysisVersion + targetDerivationVersion + zoneBuilderVersion + navigationVersion`. GIS and HYBRID snapshots are separate ids. Generate loads only a READY snapshot. FAILED/RUNNING rows are invisible. Old plans keep their snapshot FK.
