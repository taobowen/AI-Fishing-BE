# Simcoe candidate compression diagnostic

Frozen replay of current Generate Plan compression semantics. One variable per run. No strategy AI. Fixture is Simcoe-shaped (two Public Access ramps, dense mixed water near the south ramp, high-score basin 3.5 km away, lake-wide stars, two strategy-window copies).

Live Generate Plan A (trip `1f4a9b46-f94f-4093-b4f2-3a1eb0d4f4d3`, plan `f27145d6`): 17058 snapshot targets, 1670 zones, 2866 beforeDedup, 32 after, lumped `DUPLICATE` 2834, boat filter 5 accepted / 27 `BOAT_TRAVEL_UNREASONABLE`, `physicalZonesConsidered` 0, 1 waypoint, ~88 min travel, 332 min unused, beam 2422/8000.

## A–E compression results

| run | semantics | before | after | unique ids | near launch A (2 km) | near launch B (2 km) | zones represented | unassigned | TIME_VARIANT_MERGED | SPATIAL_NEAR | OVERLAP | TYPE_BUDGET | GLOBAL_QUOTA |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A | current maxTotal=32 | 2944 | 32 | 32 | 0 | 0 | 1 | 22 | 0 | 0 | 0 | 23 | 2889 |
| B | current maxTotal=64 | 2944 | 60 | 60 | 0 | 0 | 1 | 50 | 0 | 0 | 0 | 2884 | 0 |
| C | current maxTotal=128 | 2944 | 60 | 60 | 0 | 0 | 1 | 50 | 0 | 0 | 0 | 2884 | 0 |
| D | no quota; keep 150m/overlap | 2944 | 1673 | 1673 | 82 | 0 | 150 | 1501 | 831 | 440 | 0 | 0 | 0 |
| E | true-duplicate-only; quotas and 150m off | 2944 | 1964 | 1964 | 213 | 0 | 175 | 1650 | 980 | 0 | 0 | 0 | 0 |

## Mechanism isolation

- A vs D (quota removed, 150m kept): kept 32 → 1673 (quota-bound surplus **1641**).
- D vs E (150m/overlap removed, quotas still off): kept 1673 → 1964 (spatial-dedupe surplus **291**).
- Nearby dense cluster (launch A, 2 km): A=0, D=82, E=213.

## Dominating failure

**global quota** dominates this frozen replay.

150 m/overlap is not the main loss: D already keeps most of E. Replace lake-wide top-K with a stratified zone-aware shortlist. Do not restore a larger intrinsic-score top-K as the architecture.

Fixture: unique physical targets=1964, windowed rows=2944, snapshot-like zones=175, unassigned unique targets=1650.

## Live Simcoe PhysicalZone membership audit

Snapshot `777a0073-7a01-4988-978a-ede4946d3cf0` (READY, GIS, 2026-09-15). Read-only query against local PostGIS `127.0.0.1:5433`.

| metric | value |
|---|---:|
| generated targets | 17058 |
| physical zones | 1670 |
| assigned to a zone | 16380 (96.0%) |
| unassigned | 678 (4.0%) |
| skippedTargetCount / skippedByReason | 0 / {} |
| attempted partitions | 1670 |
| skipped partitions | 0 |

Unassigned is **not** skip-ledger `NOT_POLYGONAL`. Builder records no target skips. Leftovers are targets that never joined a component of size >= `cluster-min-members` (3), plus Euclidean-near targets that failed water-path linking.

### Unassigned by target kind

| kind | n | geometry |
|---|---:|---|
| PATH | 634 | ST_LineString |
| POINT | 44 | ST_Point |

Assigned: PATH 15889, POINT 491. PATH is clusterable (buffered corridor). Unassigned PATH is not a contract-level non-polygonal exclusion.

### Unassigned by feature type

| semantic_type | n |
|---|---:|
| DROP_OFF | 397 |
| HUMP | 163 |
| FLAT | 112 |
| POINT | 4 |
| BASIN | 2 |

Kind x type: PATH/DROP_OFF 397, PATH/HUMP 147, PATH/FLAT 89, POINT/FLAT 23, POINT/HUMP 16, POINT/POINT 4, PATH/BASIN 1, POINT/BASIN 1.

### Source / derivation (split_reason)

| split_reason | n |
|---|---:|
| polygon_boundary | 237 |
| orientation_change | 206 |
| max_length | 187 |
| polygon_anchor | 40 |
| whole | 4 |
| point | 4 |

### Spatial density and distance to nearest PhysicalZone

Distance to nearest zone representative (meters): min 65.7, p25 296, p50 435, p75 588, max 1756.

- within 150 m of a zone: 19
- within 550 m (neighbor search radius): 476
- within 1 km: 642
- beyond 1 km: 36

Unassigned–unassigned neighbors within 550 m: 41 isolates, 313 with one neighbor (pairs, below min-members 3), 324 with two or more (Euclidean-dense), mean 1.84 neighbors.

### Classification

- **Legitimate atomics / policy leftovers (primary):** isolates and pairs (~354) under `cluster-min-members=3`; remaining unassigned that are water-path isolated from zones even when Euclidean-near. Snapshot skip ledger is empty, so this is clustering policy, not a recorded skip bug.
- **Watch, not a proven zone-construction bug:** 324 Euclidean-dense unassigned and 476 within 550 m of an existing zone. Clustering uses water-path, not Euclidean, so this is **not** sufficient to treat them as a membership gap. Do **not** hide them in the compressor (no lake-wide duplicate drop). Treat as classified leftover atomics: shortlist buckets + bounded representative visit options. Promote to PhysicalZones only in `FishingZoneBuilder` if a later water-path audit proves they should have clustered.
- **Fixture E 1650 unassigned is synthetic**, not live Simcoe: 600 `star-*` never zoned + ~1050 of 1200 `bg-*` (only every 8th zoned). Live unassigned is 678.

Compressor must not invent a second PhysicalZone type for these 678.

## Launch B (fixture vs live)

Diagnostic D/E **0 within 2 km of launch B** is **fixture geometry**. All `SimcoeShapedField` offsets are from launch A `(44.320, -79.530)`. Launch B `(44.409, -79.580)` is ~10 km NNW; no fixture point is within 2 km.

Live snapshot near launch B: **562 targets and 43 zones within 2 km**. Nearest targets are PATH DROP_OFF at ~68 m. Launch B emptiness is not a compression effect and not a live feature-data gap. Do not pull far-basin or dense-A targets toward B in the compressor.
