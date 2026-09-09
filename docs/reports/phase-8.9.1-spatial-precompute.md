# Phase 8.9.1 Spatial Planning Model + Quality + Precomputation

Planning algorithm **1.4.0**. Flyway **V24**. Snapshot product versions: `spatial-targets-v2`, `physical-zones-v2`, `water-nav-v2`.

## A. Problem

Generate on 8.9 rebuilt targets, zones, hulls, and A* on every request. `persistSnapshot` was write-only. Split SEGMENTs shared `lakeFeatureId` plus random `coverageIds`. `IdentityHashMap` linked persist-time objects. Executable AREA/SEGMENT were stop kinds. `clusterMaxMembers` split PhysicalZones.

## B. Model

| Concept | Kind | Lifetime |
| --- | --- | --- |
| Atomic fishing target | POINT, PATH | Owned by snapshot S |
| PhysicalZone | container, boat-neutral | Owned by snapshot S |
| ZoneVisitScope | visit ZONE | Generate-time, persisted on TripPlan |
| Trip visit | POINT, PATH, ZONE | Immutable plan |

`spatialPlanningSnapshotId` is the version root. `VisitOptionKey` includes `visitScopeId` and `traversalKey` (`FORWARD|REVERSE|CLOCKWISE|COUNTER_CLOCKWISE` plus optional partial-span). No Java object identity.

## C. Snapshot job

Trigger: GIS/HYBRID analysis READY/PARTIAL, plus admin `POST /api/v1/admin/lakes/{lakeId}/spatial-snapshots`. Lifecycle: insert RUNNING → write products → validate → READY. Failure → FAILED; older READY rows stay. Unique key lock via interned string + unique constraint. Generate loads only READY S; otherwise `SPATIAL_SNAPSHOT_NOT_READY`.

Sparse per-zone nav graph. Full pairwise only under `pairwise-full-node-limit` (48). Lazy miss: Dijkstra on the existing graph, persist pair on S.

## D. Generate after READY

1. Load S + `LakePlanningGeometry` (PreparedGeometry for validation; do not rebuild the graph)
2. Strategy + weather + boat
3. Dynamic scoring on precomputed samples; PATH timestamps follow `traversalKey`
4. Derive ZoneVisitScopes; lazy water-path on S
5. ZoneSubPlanner on a scope
6. Macro: scopes + exceptional standalone POINT/PATH; collapse covered members
7. Persist visit_kind, scope members, visitEnvelope, traversal

## E. Frontend / admin

- Map: POINT marker; PATH dashed line + corridor; closed PATH highlighted; ZONE uses selected `visitEnvelope`
- Timeline: PATH labeled “path”; AREA is legacy read-only copy
- Guided: `pathFollowAim` — PATH follows `selectedFishingPath`, else entry. No dynamic replan
- Admin: `GET /api/v1/admin/trips/{tripId}/plan-diagnostics`

## F. Tests

Original 8.9 cases kept (slivers, island A*, shoreline portals, long-edge split as PATH, polygon primitives not AREA, physical cluster ZONE, directional PATH options, along-path A→B vs B→A, zone visit = fish + internal + wait, algorithm 1.4.0, GIS vs HYBRID isolation, GET immutability).

Architecture A–K:

- A atomic enum never ZONE
- B fast boat one scope / slow boat several
- C macro may pick two non-overlapping scopes
- D no member double-count
- E polygon primitives without a single edge PATH
- F large zone does not require full all-pairs
- G lazy path reuses graph (unit) and persists on S (IT)
- H FAILED snapshot invisible to Generate
- I concurrent same-key rebuild idempotent
- J old READY survives new-key FAILED
- K CLOCKWISE vs COUNTER_CLOCKWISE distinct VisitOptionKeys

## G. AFTER profile

Live Rice Lake GIS Generate was **not** re-run against the developer PostGIS in this implementation session (the app process was not on 1.4.0/V24). Do not invent Rice AFTER stage times.

Synthetic AFTER (Head-sized IT lake, READY snapshot): Generate must show `TARGET_BUILD` / `TARGET_SPLIT` / `STATIC_SAMPLE_BUILD` / `PHYSICAL_ZONE_BUILD` / graph rebuild ≈ 0. `STATIC_SPATIAL_LOAD` is a DB read. A* rasterization must not recur; lazy paths use `ZoneNavGraph.shortest`. Profiler JSON is written to `PlanningRun.usageMetadata.profiler`.

BEFORE Rice (8.9, same trip shape): total **~265 s**, route **~257 s**. See [phase-8.9.1-before.md](phase-8.9.1-before.md).

When ops applies V24 and rebuilds Rice GIS spatial snapshot, compare `usageMetadata.profiler` on the same trip. Expected: static build stages near zero; MACRO_ROUTE_SEARCH should drop because water-path/A* raster work moved out of Generate.

## H. Quality

New writes never emit executable AREA. Polygon sources keep honest POINT/PATH primitives (`DATA_LIMITATION` only when no primitive is supportable). Vegetation is not invented. CPUE grain remains `min(catch, effort)` with no hierarchy double-count.

Device map screenshots were not captured (no 1.4.0 live plan on device in this session). FE unit tests cover PATH overlay, ZONE `visitEnvelope`, closed-loop highlight, and `pathFollowAim`.

## I. Ops

1. Apply Flyway V24
2. `POST /api/v1/admin/lakes/{riceId}/spatial-snapshots?pipeline=GIS`
3. Generate the same Rice GIS trip
4. Read `GET /api/v1/admin/trips/{tripId}/plan-diagnostics` and planning-run `usageMetadata.profiler`

Stop after 8.9.1. No Phase 9. No realtime adaptive guidance.
