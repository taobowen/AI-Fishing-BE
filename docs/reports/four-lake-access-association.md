# Four-lake access association (observed)

- generatedAt: `2026-09-04T02:10:53Z` (Simcoe ACCESS_POINT-only import completed)
- environment: `local-live`
- apiBase: `http://127.0.0.1:8080`
- PostGIS: `127.0.0.1:5433/aifishing`
- import: `POST /api/v1/admin/lakes/{id}/import?dataset=ACCESS_POINT` only (no wetland / species / index refresh)
- association: union of `lake_boundaries` (fallback `lakes.boundary`); inside water or ≤ 300 m (`LocalMetricCrs`)

Live LIO Open07 layer 15. Not fixture counts.

## Summary

| Lake | raw LIO | canonical | rejected-by-distance | Boat Launch | Shoreline Access | gov / PRIVATE / UNKNOWN | AUTO-eligible (not PRIVATE) | GET `/boat-launches` (routable) |
| --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | --- |
| Head Lake | 0 | 0 | 0 | 0 | 0 | — | 0 | 0 (0) |
| Rice Lake | 14 | 7 | 7 | 7 | 0 | 0 / 0 / 7 | 7 | 7 (7) |
| Lake Scugog | 10 | 10 | 0 | 9 | 1 | 0 / 5 / 5 | 4 | 9 (9) |
| Lake Simcoe | 105 | 90 | 15 | 88 | 2 | 8 / 17 / 65 | 72 | 88 (84) |

gov = `MUNICIPAL` + `PROVINCIAL` + `FEDERAL` + `PUBLIC`. Simcoe gov split: 6 municipal, 2 provincial. Scugog 5 PRIVATE / 5 UNKNOWN includes the shoreline-access row.

Distance buckets on **associated** rows:

| Lake | ≤ 80 m | 80–250 m | 250–300 m | max m |
| --- | ---: | ---: | ---: | ---: |
| Head | — | — | — | — |
| Rice | 7 | 0 | 0 | 8.4 |
| Scugog | 10 | 0 | 0 | 20.1 |
| Simcoe | 79 | 7 | 4 | 296.0 |

Haliburton village `"Head Lake"` at `-78.5135, 45.0452` is **not** in catalog Head canonical (0 rows). Head catalog OGF still has 0 LIO features in the ingest envelope.

## Representative rejects (bbox hit, not associated)

Rice (7): Sherin Ave. Boat Ramp, Monaghan Street Boat Ramp, Mervin Line (shoreline), Bensfort Bridge Resort - Private, DFO Campbelltown, DFO Bensfort Bridge, one unnamed Boat Launch.

Simcoe (15): City of Orillia Centennial Park (~2.4 km), Everglades Marina, Quinn's Marina, Beaverton Yacht Club, Crates Willow Marina Beach, Trent-talbot Marina, River Garden Family Restaurant, several Emergency Access / Public Access / unnamed.

Those names are absent from `GET /boat-launches` after refresh.

## Fingerprints

`lake_analysis_runs.source_snapshot_id` **immediately after** ACCESS_POINT-only import (no process):

| Lake | GIS / VISION / HYBRID `sourceSnapshotId` |
| --- | --- |
| Head | `src-5575f7110ef1e1ffee9cae74872aae2a6bcc8e03eb20df74f4dfdb66326607bb` |
| Rice | `src-a9d399918aebdb7a515c5b7b6765244ac6d5c6f99d3f82ea1e7f6872bb482b05` |
| Scugog | `src-460c8a53ee29fb83d98f22e7405af044ceb08dc07678c198d320720891d5ea94` |
| Simcoe | `src-d2ee288e0a9ba4d2acf0703561423b0384cf3fc50585cf3b34b2368c23a397cf` |

Unchanged from the values stored before this import. ACCESS_POINT-only did not rewrite other datasets’ canonical rows.

Structure fingerprints no longer include `ACCESS_POINT` status. That is a one-time hash-schema change: Generate Plan compared the stored ids above to the new hash and returned `STALE_OR_MISSING_FEATURE_SNAPSHOT` until GIS was re-run. Subsequent ACCESS_POINT-only imports will not move that hash.

GIS reprocess after this report’s import (to smoke Generate Plan only; not required for association):

- Head GIS READY ~1.7 s → analysisVersion `c928f2bd-c2e1-4f8e-b7c7-2713effacae8`
- Scugog GIS READY ~1.7 s → `1f9f48d6-5285-4667-a2dc-10879355e95b`
- Rice GIS READY ~142 s
- Simcoe GIS / all VISION / HYBRID **not** reprocessed here

## Phase 8.7 smoke

| Check | Result |
| --- | --- |
| `GET .../boat-launches` Head | 0 |
| `GET .../boat-launches` Rice / Scugog / Simcoe | 7 / 9 / 88; 11–14 km Rice and Centennial Park gone |
| PRIVATE in picker | Scugog Caesarea / Goreski’s with `PRIVATE_LAUNCH_PERMISSION_REQUIRED` |
| AUTO omits PRIVATE | Rice AUTO used UNKNOWN (`OWNERSHIP_UNVERIFIED`, `accessConfidence` 0.55). Scugog AUTO-eligible is the 4 non-PRIVATE ramps |
| Head AUTO | `NO_KNOWN_BOAT_LAUNCH` |
| Head CUSTOM preview at centroid | water-side `routeStartPoint` present (`ISLAND_OR_INTERIOR_RING`) |
| OFFICIAL_SELECTED listed launch | Scugog `Cartwright Road` (UNKNOWN) created as OFFICIAL_SELECTED. Generate then `NO_CANDIDATES` (sparse Scugog GIS / 80 m SHORE filter — not a launch mapping failure) |
| Rice AUTO Generate Plan (GIS) | `COMPLETED` / `AUTO_RECOMMENDED` / `OWNERSHIP_UNVERIFIED` / 0.55 |

## Remaining blockers (out of scope)

- Head SHORE `NO_CANDIDATES` / `SHORE_INACCESSIBLE` (80 m candidate filter)
- Scugog boat Generate Plan `NO_CANDIDATES` (7 GIS features; 80 m filter)
- Simcoe `FISH_SPECIES` / `WETLAND` / `BATHYMETRY_INDEX` FAILED
- Simcoe GIS + all VISION/HYBRID still pinned to the pre-fingerprint-schema `sourceSnapshotId` until next process
- Some live `SITE_NAME` values are empty (Rice unnamed ramps); mapper still prefers `SITE_NAME`
