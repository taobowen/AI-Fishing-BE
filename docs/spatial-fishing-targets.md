# Spatial fishing targets (Phase 8.9.1)

A fishing stop is a **spatial visit**, not a collapsed water Point. Phase 8.8 time-aware scheduling stays. Generate Plan consumes one immutable READY `SpatialPlanningSnapshot` (version root). Atomic `FishingTargetKind` is **POINT | PATH** only. Trip visits are **POINT | PATH | ZONE**. ZONE is never stored on `lake_fishing_targets`. Legacy `SEGMENT` / `AREA` remain read-only.

There is no `TimeAwareScheduler` class. Scheduling lives in `RoutePlanner` plus `TimeAdjustedSpotUtility`.

## Frozen 8.8 behavior (keep)

- Instant / `TIMESTAMPTZ` schedule (`plannedArrivalAt`, `plannedDepartureAt`, launch/return)
- WAIT at launch or current stop (`schedule_events`)
- Weather from the StrategyRun snapshot (no Open-Meteo at Generate)
- Solar / wind / temperature adjustments and Why this time
- Boat capability + launch `routeStartPoint`
- Return buffer and beam determinism (`beam-width` 8)
- Immutable GET plan (no rewrite of stored 8.8 rows)
- GIS / HYBRID snapshot isolation (`featurePipeline` + `featureAnalysisVersion`)
- Macro whole-lake travel: `TravelTimeEstimator` geodesic × detour (1.35) / land-crossing (2.2). Not a marine router.

## What 8.8 collapsed (must change)

`CandidateSpot.sourceGeometry` existed but planning used only `CandidateLocationService` → one water `Point`. Ranking, beam cursor, persist, Mapbox, guided arrival, and FISH ON all used that Point.

## Target kinds vs semantics

- **Atomic kind** (`POINT` | `PATH`): persisted on `lake_fishing_targets`.
- **Visit kind** (`POINT` | `PATH` | `ZONE`): scheduled on the TripPlan. `ZONE` is a container of atomics (a ZoneVisitScope), never an atomic target.
- Legacy **SEGMENT** / **AREA** remain readable on old waypoints.
- **Semantic** (`FeatureType`): HUMP, DROP_OFF, FLAT, POINT, BASIN, ISLAND_EDGE. Optional shoreline/weed/channel tags only when derivation can justify them.

ZONE is a composite envelope of member targets, not an AREA.

## Target geometry vs boat-fishable geometry

Shoreline / island **PATH** `targetGeometry` may lie on the physical break. Boat portals are **not** LineString endpoints on land. Closed loops expose CLOCKWISE / COUNTER_CLOCKWISE `traversalKey`s.

Derived and persisted:

- `fishingCorridor` — water-side work strip
- navigable `entryPoint` / `exitPoint` (`LakePlanningGeometry.validFishingPoint`)
- `selectedFishingPath` when a path fits visit fishing time × technique speed

The corridor/path is where the boat works. The LineString is the fishing feature.

## Concave hull (production, locked)

Hibernate Spatial on this stack provides **JTS 1.19.0**. Zone envelopes use
`org.locationtech.jts.algorithm.hull.ConcaveHull` in `LocalMetricCrs` with a fixed
`maximumEdgeLengthRatio` (`app.planning.spatial.concave-hull-edge-length-ratio`, default 0.35).

Same Java path in unit tests and live Generate. There is **no** PostGIS `ST_ConcaveHull`
fallback. Empty/invalid hull after water-intersect → reject the Zone (no bounding box).

## Visit-time semantics

| Field | Meaning |
| --- | --- |
| `plannedVisitMinutes` | Arrival→departure of the complete stop. |
| `plannedDwellMinutes` | **Compatibility alias of `plannedVisitMinutes`** (full visit, not fishing-only). |
| `plannedFishingMinutes` | Time actually fishing (excludes internal transit and wait). |
| `plannedInternalTransitMinutes` | Water-path movement inside the stop. |
| `plannedWaitMinutes` | Optional wait inside the visit. |

Zone visit = fishing + internal transit + internal wait. Plan `totalFishingMinutes` excludes Zone internal transit. `totalTravelMinutes` and the return deadline include it.

## Along-path time coupling

Do not average all spatial samples against all time slots.

For a selected Segment direction over visit fishing time T, sample i at fraction f is scored at `arrival + f × T`. A→B and B→A can differ. AREA/ZONE bind samples to the actual internal subplan schedule.

Aggregate = decay-weighted mean of the ordered high-value fraction plus a continuity term. Exact weights: `TimeAdjustedSpotUtility` dwell decay on the sample Instants (`app.planning.schedule.dwell-decay`).

## Planning knobs (8.8 + 8.9 spatial)

Existing `app.planning`: `algorithm-version`, `candidates.*`, `ranking.*`, `schedule.*`, `environment.*`, `travel.*`, `access.*`, `boat.*`, `safety.*`.

New `app.planning.spatial`: corridor widths, segment split length, cluster connectivity/diameter/members, sliver area, concave-hull ratio, local A* cell size / max cells, internal cruise km/h, portal caps, sample spacing.

Algorithm version for 8.9.1: **1.4.0**. Snapshot product versions: `spatial-targets-v2`, `physical-zones-v2`, `water-nav-v2`. Flyway **V24**.

See also [hierarchical-zone-planning.md](hierarchical-zone-planning.md).
