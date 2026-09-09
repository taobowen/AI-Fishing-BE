# Phase 8.9 spatial fishing targets (report)

Planning algorithm version: **1.3.0**. Flyway **V23**.

## What shipped

- Atomic `POINT` / `SEGMENT` / `AREA` targets plus strategy-neutral physical `ZONE` snapshots (`lake_fishing_targets`, `lake_fishing_zones`).
- Production JTS 1.19 `ConcaveHull` in metric CRS. No PostGIS hull fallback.
- Water-side corridors and navigable entry/exit portals (shoreline vertices are not boat portals).
- Zone visits as explicit `(entryPortal, exitPortal)` pairs. Macro beam cursor is the exit portal.
- Local navigable-water grid A* for Zone internals (no corner-cutting through land). Macro hops stay on geodesic `TravelTimeEstimator`.
- Visit minutes: `plannedVisitMinutes` (= compatibility `plannedDwellMinutes`), `plannedFishingMinutes`, `plannedInternalTransitMinutes`. Plan `totalFishingMinutes` excludes Zone internal transit; travel totals include it.
- Along-path samples scored at the Instant the boat would reach that fraction. A→B can differ from B→A.
- CPUE grain = min(catch resolution, effort resolution). Zone effort without target effort does not write micro CPUE.
- GiST indexes on target/zone geometry and representative points.
- Mapbox Point / Segment / Area / Zone overlays; expandable Zone timeline; guided arrival uses entry/corridor.

## Tests

Synthetic (`SpatialPlanningTest` and existing planning ITs):

- point compatibility + GET `targetKind` on generate
- island-edge LineString with water-side portals
- long-edge split
- irregular AREA (HUMP polygon)
- island/drop-off cluster ZONE (synthetic water)
- geometry validity / slivers / empty reject
- A* does not cross island
- mutual exclusion via coverage IDs
- SEGMENT A→B vs B→A visit options
- along-path sample order
- 60 min Zone subplan: `visit = fish + internal transit + wait`
- algorithm version `1.3.0`; GIS vs HYBRID still isolated by pipeline + analysisVersion (`UserGeneratePlanIT`)
- GET immutability of stored plans (no rewrite of 8.8 rows)
- FISH ON persists `fishingTargetId` / `zoneId`; effort at Zone grain does not write micro CPUE keys

Performance (synthetic, unit): local A* around a 120 m island on a 25 m grid is sub-millisecond; Zone subplan cache is keyed by exact portal pair.

## Live SEGMENT / ZONE

Auto-pick among Head / Rice / Scugog / Simcoe from current PostGIS (`127.0.0.1:5433`):

| Lake | DROP_OFF LINESTRING | Pairs within 220 m | ISLAND_EDGE |
| --- | ---: | ---: | --- |
| Lake Simcoe | 942 | 11,783 | 26 polygons, 0 pairs within 220 m |
| Head Lake | 60 | 354 | 0 |
| Rice Lake | 80 | 232 | 2 polygons, 0 pairs within 220 m |
| Lake Scugog | 5 | 0 | 1 polygon |

**SEGMENT:** live LINESTRING drop-offs exist on Head, Rice, and Simcoe. Auto-pick for a compact SEGMENT check is **Head**; densest field is **Simcoe**.

**ZONE:** no island-edge cluster is navigably connected at `cluster-connect-m` 220 (`DATA_LIMITATION` for island-cluster ZONEs). Physical ZONEs can form from **DROP_OFF** clusters — Simcoe is the densest coherent set. Scugog remains `DATA_LIMITATION` — not a forced envelope.

Live Generate against the developer database was not run in this session (V23 is in the repo; applying it to the running app DB is an ops step). Synthetic ZONE + SEGMENT coverage is complete.

## Out of scope

Realtime micro-guidance, Phase 9, whole-lake certified marine routing, inventing Scugog zones.
