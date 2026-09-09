# Phase 8.9.1 BEFORE profile (Rice Lake GIS)

Date: 2026-09-04. Algorithm **1.3.0**, Flyway **V23**. Generate still rebuilt spatial products on every request (`CandidateGenerator` → `FishingTargetBuilder.enrich` → `FishingZoneBuilder.cluster` → envelope A* → `RoutePlanner`).

## Method

`GenerateProfiler` stages/counters were added in 8.9.1 (`STATIC_SPATIAL_LOAD`, `TARGET_BUILD`, `TARGET_SPLIT`, `STATIC_SAMPLE_BUILD`, `PHYSICAL_ZONE_BUILD`, `PORTAL_BUILD`, `WATER_PATH_BUILD`, `OPERATIONAL_VISIT_BUILD`, `DYNAMIC_TIME_SCORING`, `ZONE_SUBPLAN`, `MACRO_ROUTE_SEARCH`, `PERSIST`, `TOTAL`; A* calls/hits; ZoneSubPlanner; beam expansions).

This BEFORE table is the last **successful live Rice Lake GIS Generate** on the 8.9 path, **not** a guessed breakdown. Per-stage wall times other than total/route were not recorded on that run; they are left blank rather than invented.

Lake: Rice Lake (米湖). Trip shape: GIS Standard Plan, existing COMPLETED StrategyRun.

## Observed

| Stage | ms |
| --- | ---: |
| STATIC_SPATIAL_LOAD | n/a (no snapshot; rebuild in Generate) |
| TARGET_BUILD | (not separately timed) |
| TARGET_SPLIT | (not separately timed) |
| STATIC_SAMPLE_BUILD | (not separately timed) |
| PHYSICAL_ZONE_BUILD | (not separately timed) |
| PORTAL_BUILD | (not separately timed) |
| WATER_PATH_BUILD | (not separately timed) |
| OPERATIONAL_VISIT_BUILD | (not separately timed) |
| DYNAMIC_TIME_SCORING | (not separately timed) |
| ZONE_SUBPLAN | (not separately timed) |
| MACRO_ROUTE_SEARCH | **~257_000** |
| PERSIST | (not separately timed) |
| **TOTAL** | **~265_000** |

Counters: A* rasterization ran inside Generate for zone internals / water paths (unbounded relative to snapshot reuse). Beam expansions were not exported on that run.

## Interpretation

Almost all wall time sat in Generate-time spatial rebuild + route search (~257 s of ~265 s). That is the defect 8.9.1 removes: static products must be built once into a READY `SpatialPlanningSnapshot` and loaded by id.

AFTER must use the same lake + same trip shape, with profiler JSON on `PlanningRun.usageMetadata`.
