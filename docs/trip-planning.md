# Trip planning (Phase 5)

Phase 5 turns a **COMPLETED** Phase 4 `StrategyRun` plus PostGIS `lake_features` into a versioned **TripPlan** / **TripWaypoint** list. Phase 4 decided *what* to prefer (structure types, depths, techniques). Phase 5 decides *where* those structures exist and in what order to fish them.

**User Generate Plan** (`POST /api/v1/trips/{tripId}/plan`) verifies trip ownership, then either:

- omitted / `"featurePipeline":"GIS"` → **Standard Plan** (user default `app.planning.user-default-feature-pipeline`, GIS)
- `"featurePipeline":"HYBRID"` → **AI-Enhanced Plan (Experimental)**
- `"featurePipeline":"VISION"` → `400 FEATURE_PIPELINE_UNSUPPORTED`
- `strategyRunId` **and** `featurePipeline` → `400 GENERATE_PLAN_REQUEST_CONFLICT`
- `strategyRunId` alone → plan against that COMPLETED run’s recorded pipeline + analysisVersion

Omitted normal-user requests always use the planning user default, **not** `app.strategy.feature-pipeline`. Changing the admin YAML default does not change omitted Generate Plan.

`GET /api/v1/lakes/{lakeId}/planning-capabilities` is a read-only UX check for GIS and HYBRID. Generate Plan still validates readiness server-side and never auto-processes Hybrid. Admin `POST /admin/trips/{id}/strategy` remains YAML `app.strategy.feature-pipeline` (GIS). Lakes must already be processed; Generate Plan does not call `POST /admin/lakes/{id}/process`. Live Head Standard + AI-Enhanced generate (exact pipeline/analysisVersion, no GIS fallback): [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md).

The mobile app offers **Standard Plan** and **AI-Enhanced Plan (Experimental)**. Do not label buttons Generate GIS / Generate Hybrid. VISION and Direct Screenshot Vision are not user choices.

The LLM never chooses coordinates. Failed regeneration preserves the last `GENERATED` or `ACCEPTED` plan.

Production default structure pipeline remains GIS. There are no Head / Rice / Scugog / Simcoe branches in planning code.

## Contract with Phase 4

Planning binds to the StrategyRun’s **recorded** `featurePipeline` and `featureAnalysisVersion`. It does **not** read live `app.strategy.feature-pipeline` or `lakes.current_analysis_version`.

- Strategy generate (Phase 4 follow-up) writes `trip_strategy_runs.feature_analysis_version` from the last analysis run for that pipeline.
- If that column is null, or no `lake_features` remain for that exact lake + pipeline + analysis version → planning run **FAILED** (`STALE_OR_MISSING_FEATURE_SNAPSHOT`). Do not retarget “latest GIS.”
- `STRUCTURE_NONE_AFTER_ANALYSIS` on the recorded profile with an empty snapshot → **FAILED** (`STRUCTURE_NONE_AFTER_ANALYSIS`). Do not invent spots.
- Changing YAML `app.strategy.feature-pipeline` does not invalidate a historical COMPLETED run that still has its feature snapshot.
- Weather for safety, time-indexed environment, and boat derate is the persisted `weather_snapshot`. Open-Meteo is not called at Generate Plan. See [time-aware-planning.md](time-aware-planning.md).
- `GET /api/v1/trips/{id}` stays trip-only.

Phase 8.9.1: GIS/HYBRID analysis READY builds an immutable `SpatialPlanningSnapshot` (POINT/PATH atomics, PhysicalZones, sparse nav graph). Generate Plan loads **only** that READY snapshot by id — it does not rebuild targets/zones/A*. If no READY snapshot exists, the run fails `SPATIAL_SNAPSHOT_NOT_READY`. Beam travel is previous **exit portal** to next **entry portal**. See [spatial-fishing-targets.md](spatial-fishing-targets.md) and [hierarchical-zone-planning.md](hierarchical-zone-planning.md). Algorithm version is `1.4.0`.

## Flow

```text
User POST /trips/{id}/plan  ({ "featurePipeline": "GIS"|"HYBRID" } or omitted → Standard/GIS)
        │
        ▼
StructurePipelineReadinessService (GIS or HYBRID) — fail typed if unavailable; never GIS fallback
        │
        ▼
FishingStrategyService.generate(tripId, pipeline)  →  new StrategyRun records pipeline + analysisVersion
        │
        ▼
COMPLETED StrategyRun
        │
        ▼
BoatCapabilityResolver (once, BOAT only) → EffectiveBoatCapability from persisted weather
        │
        ▼
Coarse lake_features query  (lake + recorded pipeline + recorded analysis version + type + depth)
        │
        ▼
CandidateLocationService (JTS representative point, water-validated)
        │
        ▼
Launch-independent filters (regs, accessibility, safety / geometry)
        │
        ▼
BOAT AUTO: LaunchRecommender on intrinsic fishing quality + per-launch travel
        │
        ▼
Hard filters that need a launch (BoatCapabilityFilter) then SpotRankingService
        │
        ▼
ResolvedTripLaunch.routeStartPoint → RoutePlanner (time-aware beam search) → Instant schedule
        │
        ▼
TripPlanValidator
        │
        ▼
trip_planning_runs  +  trip_plans / trip_waypoints
```

## Spatial split

**PostGIS / repository (coarse):** `LakeFeatureRepository.findCandidates` filters `lake_id`, `pipeline`, `analysis_version`, `type IN (...)`, and depth overlap. Only that shortlist is loaded. Planning does not `findByLakeId` and scan every Simcoe feature.

**JTS (fine):** representative points, covered-by-water / not-on-island, geometry overlap for dedupe, and land-crossing of a route edge. Lake boundary + island waterways are loaded for those checks; full-lake feature geometries are not.

Travel times are a **heuristic**, not certified marine navigation.

## Representative points

| Type | Rule |
| --- | --- |
| HUMP / FLAT / BASIN | JTS `InteriorPointArea` (not a raw centroid, which can fall outside a concave polygon) |
| DROP_OFF | Midpoint of the longest segment, then ~15 m lake-ward if the midpoint still sits on the line |
| POINT | Stored point |
| ISLAND_EDGE | Water-side point in lake water minus islands, within a small buffer |

Invalid locations try a few deterministic offsets, then reject (`NO_WATER_POINT` / `INVALID_LOCATION`). The `lakeFeatureId` is always kept.

## Time-window weights

`StrategyWeightResolver` uses Phase 4 precedence:

1. Window `structurePreferences` / `techniques` are effective for `[from, to)`.
2. Globals fill types/techniques the window omits, or if the window lists are empty.
3. **Do not average** window + global.

Query types with effective weight `> 0.05`. Optional second pass widens depth by `fallback-depth-tolerance-m`.

## Hard filters

Rejected candidates never come back via score. Counts are stored on `trip_planning_runs.filter_summary`.

**Regulations** use Phase 2 structured rows with authoritative geometry and dates. **`rawText` is never parsed.**

- Whole-area `NO_FISHING` / `SANCTUARY` / `CLOSED` (species null) covering the point → `REGULATION_WHOLE_AREA`.
- Restriction species = trip **primary** target → `REGULATION_PRIMARY_SPECIES`.
- Restriction species = **secondary** only → keep the spot; waypoint `secondaryTargetRestricted`.
- Dataset coverage `PARTIAL` / `NOT_AVAILABLE` → plan warning; keep unless a geom rule hits.

**SHORE:** shoreline proximity is not public access. Prefer `LakeAccessPoint.shoreAccess`. Otherwise `SHORE_ACCESS_UNVERIFIED`, accessibility score `0.35`, warning. Missing shore-access rows are not a hard reject by themselves.

**BOAT:** reachability uses `ResolvedTripLaunch.routeStartPoint()` (Phase 8.7), not a lake centroid and not a mutated `lake_access_points` coordinate. Feasibility uses **Phase 8.6 effective boat capability** (resolved cruise / usable range after weather derate), not hull type alone. Unknown range falls back to a conservative max-leg from YAML / `boat.max-one-way-km`. See [boat-capability.md](boat-capability.md) and [boat-launch-selection.md](boat-launch-selection.md). Fishing scores are not scaled by boat range.

**Safety:** wind from the strategy snapshot. ≥ 40 km/h hard-rejects BOAT. ≥ 25 km/h is a ranking penalty. Missing weather → warning, no fabricated wind. Not a certified safety system.

## Ranking

`finalScore = Σ weight_i × component_i` from `app.planning.ranking` (must sum to 1.0):

| Component | Meaning |
| --- | --- |
| strategyMatch | effective structure weight |
| depthMatch | 1 inside the window range; linear decay outside using fallback tolerance |
| featureConfidence | `LakeFeature.confidence` |
| timeWindowMatch | 1 if the window listed the type; lower for global fill |
| gearCompatibility | light TechniqueType ↔ GearType map; **empty gear → 0.5** |
| weatherCompatibility | **Not used in intrinsic ranking as of Phase 8.8.** Time-indexed solar/wind/temperature live on the scheduler utility instead ([time-aware-planning.md](time-aware-planning.md)) |
| travelAccess | 1 − distance/maxOneWay from `routeStartPoint` when a launch is resolved; AUTO recommendation holds this at 0.5 so launch proximity cannot leak into launch choice |
| historicalPerformance | shrinkage-smoothed landed CPUE vs prior; no/low evidence → **0.5** ([fishing-feedback.md](fishing-feedback.md)). `historicalEvidenceConfidence` is stored but is not a weight. |

Do **not** multiply by Phase 4 `systemConfidence`. Plan confidence is `0.5 * systemConfidence + 0.5 * meanFeatureConfidence`, clamped `[0.05, 0.95]`.

Explanations are templates (type, depth, confidence, window, Phase 4 rationale snippet). No extra LLM call.

## Access (never the lake centroid)

BOAT launch selection is the user source of truth. See [boat-launch-selection.md](boat-launch-selection.md).

1. Trip `OFFICIAL_SELECTED` / `CUSTOM_SELECTED` — use that selection. Conflicting `GeneratePlanRequest.accessPointId` → 400 `LAUNCH_SELECTION_CONFLICT`.
2. Trip `AUTO_RECOMMENDED` — `LaunchRecommender` after launch-independent candidates (intrinsic fishing quality, then travel). Optional request `accessPointId` is a **run-scoped** official override and is not persisted.
3. SHORE — `AccessPointSelector` (`shoreAccess` / `SHORE_ACCESS_UNVERIFIED`). Launch UI is hidden.

Custom snaps use a **projected metric CRS** (not degree buffers). CUSTOM Generate Plan uses the persisted `routeStartPoint` unless the resolution is invalid or the version migrated.

Missing `boat_launch=true` rows → **FAILED** `NO_KNOWN_BOAT_LAUNCH`. Known launches that cannot be water-anchored → **FAILED** `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`. Do not collapse those codes. Do not invent a centroid origin.

SHORE UNKNOWN: no fabricated launch coordinate; no travel-from-access or return-to-launch; inter-spot travel still estimated; first waypoint travel is unknown with `ACCESS_UNKNOWN`; skip the return buffer.

## Route, time, and land crossing

Time-aware beam search jointly chooses sequence, Instant arrival, dwell, optional WAIT, and return ([time-aware-planning.md](time-aware-planning.md)). Static intrinsic order can reverse when a later window is better. Stay is chosen from configured dwell options with diminishing returns, not a linear 45–90 interpolation.

Route edges: geodesic meters × `detour-factor`. If the segment is not covered by lake water or intersects an island, apply `land-crossing-detour-factor` (2.2) and set `landCrossingDetected`. Do not report a naive over-land time. Still not marine routing.

Travel-leg wind is evaluated on the snapshot interval (including return). Totals include the return leg. `plannedArrivalAt` / `plannedDepartureAt` / `plannedLaunchDepartureAt` / `plannedReturnAt` are `TIMESTAMPTZ`.

If a window has no survivors, global-prior candidates for that hour may be used (`WINDOW_FALLBACK_GLOBAL`). Zero after filters → FAILED. 1–2 spots on a long day → COMPLETED with `SPARSE_CANDIDATES`.

## Persistence and versioning

Short `REQUIRES_NEW` insert `RUNNING` → compute with no open session → short persist `COMPLETED`+plan or `FAILED`.

Successful generate: previous `GENERATED` (not `ACCEPTED`) → `SUPERSEDED`, insert `version = max+1` as `GENERATED`. `FAILED` lives on **PlanningRun**, not TripPlan. Failed regen does not delete the last plan.

## APIs

User (owned trip):

| Method | Path |
| --- | --- |
| POST | `/api/v1/trips/{tripId}/plan` optional `{ strategyRunId, accessPointId }` (`accessPointId` is AUTO run-scoped override only) |
| GET | `/api/v1/trips/{tripId}/plan` latest non-SUPERSEDED GENERATED/ACCEPTED |
| GET | `/api/v1/trips/{tripId}/plans` |
| GET | `/api/v1/trips/{tripId}/plan/map-data` waypoint points/sequence/featureId/type only |

400 if no COMPLETED strategy. 404 if no plan yet. Locations are `{lat, lng}` (`GeoPointDto`).

Admin (`app.admin.enabled`):

| Method | Path |
| --- | --- |
| GET | `/api/v1/admin/trips/{tripId}/planning-runs` |
| GET | `/api/v1/admin/trips/{tripId}/planning-runs/{runId}` (`filter_summary`) |

## Config

See `app.planning` in `application.yml`. Type `max-one-way-km` is a **fallback max-leg** when practical range is unknown — not the primary boat story. Resolved cruise, usable range, and wind-wave class are documented in [boat-capability.md](boat-capability.md). None of these are certified navigation rules.

## Four-lake fixtures

Tests seed Head / Rice / Scugog / Simcoe with **different feature counts and spacing**. Assert that plans differ because the data differs. Do not invent live Ontario feature counts here.

## MVP limits

Heuristic travel, no ML ranking, no Garmin, no certified marine navigation, no lake recommendation engine. Live GPS belongs to Phase 6 ([guided-fishing-session.md](guided-fishing-session.md)). FISH ON and empirical ranking belong to Phase 7 ([fishing-feedback.md](fishing-feedback.md)). Launch selection belongs to Phase 8.7 ([boat-launch-selection.md](boat-launch-selection.md)).

## Phase 6 guided session handoff

Guided session loads the current `GENERATED` or `ACCEPTED` plan and its waypoint sequence. It compares GPS to planned points. It does **not** re-rank. Planning never starts a session.

See [guided-fishing-session.md](guided-fishing-session.md).
