# Lake structure extraction (Phase 3 / 3.1)

Phase 3 consumes Phase 2 canonical tables and writes planning-usable lake structure. Phase 3.1 adds canonical-render Vision, Hybrid merge, and a structure-quality benchmark. It does **not** build Planning JSON, rank spots, generate routes, or track GPS / FISH ON / feedback.

Production default remains **GIS**. `lakes.processing_status` is the GIS summary so clients do not silently switch to Vision. User Generate Plan **Standard** = GIS; **AI-Enhanced (Experimental)** = HYBRID when that snapshot is product-ready. Generate Plan never runs `process?pipeline=HYBRID`. See [structure-benchmark.md](structure-benchmark.md) and [trip-planning.md](trip-planning.md).

User `GET /api/v1/lakes` stays read-only and does not return processing or vision details.

## Inputs (read-only)

| Table | Used for |
| --- | --- |
| `bathymetry_contours` / `bathymetry_points` | HUMP, DROP_OFF, FLAT, BASIN |
| `lake_waterways` (`SHORELINE`, `ISLAND`) | POINT, ISLAND_EDGE |
| `lake_boundaries` | clip features to the lake |

If both `BATHYMETRY_LINE` and `BATHYMETRY_POINT` are `NOT_AVAILABLE`, bathymetry extractors are not runnable. They are marked `NOT_AVAILABLE` and the last successful features for those types are kept. Shoreline extractors can still run.

## Feature-type replace

Each `FeatureType` is extracted, validated, and replaced in its own transaction:

1. `lastAttemptedAt = now`
2. Missing required sources → `NOT_AVAILABLE` (do not delete last good rows)
3. Extract (contour/topology first)
4. Validate **all** records for that type
5. Success: delete `(lake_id, type, pipeline)` then insert the new set; `lastSuccessfulAnalysisAt = now`; `AVAILABLE` (including `AVAILABLE` + 0 rows)
6. Failure: `FAILED`; previous successful rows and `lastSuccessfulAnalysisAt` stay

HUMP succeeding does not replace FLAT. The lake can be `PARTIAL`. GIS, VISION, and HYBRID rows coexist (`pipeline` on `lake_features` / status / runs). There is no `SCREENSHOT` pipeline.

There is no whole-lake all-or-nothing commit. `ProcessingJobRunner` is synchronous Spring today; a worker (SQS) can replace it later.

## Status

`lakes.processing_status` is the **current summary only**. History lives in `lake_analysis_runs`.

| Lake status | Meaning |
| --- | --- |
| `READY` | Every *runnable* extractor succeeded. Types that are `NOT_AVAILABLE` because sources are missing do not block READY. |
| `PARTIAL` | At least one runnable extractor wrote a usable new result **and** at least one runnable extractor failed. |
| `FAILED` | No usable new result, or a core analysis step failed. Vacuous lakes with no runnable extractors are `FAILED`. |

Type-level status still distinguishes `AVAILABLE` (including 0 rows), `NOT_AVAILABLE`, and `FAILED`.

Analysis terminology: `analysisVersion`, `lastAttemptedAt`, `lastSuccessfulAnalysisAt`. Phase 2 import versions are stored on `sourceDatasetSnapshot`, not as a derived `importVersion` on features.

Derived features do **not** require `sourceRecordId`. Provenance is `analysisVersion` + `sourceDatasetSnapshot` + `derivationMetadata`.

## Algorithms (stable MVP, not Garmin-grade)

All thresholds are `app.processing.*`. There are **no Head / Rice / Scugog / Simcoe branches**.

Contour/topology is the default path. A whole-lake dense raster is **not** built. Local/tiled PostGIS Raster would be used only if an extractor needed interpolation; current extractors do not.

| Type | Detection |
| --- | --- |
| **HUMP** | Closed contour nested inside a deeper parent (shallower local high). |
| **BASIN** | Closed contour nested inside a shallower parent (local deep). **Only `BASIN` is persisted.** `HOLE` is not a stored type. |
| **DROP_OFF** | Nearby contours of different depth with spacing ≤ `dropoff-max-spacing-m` and gradient ≥ `dropoff-min-gradient`. Geometry is a `LineString`. |
| **FLAT** | Large closed contour (`flat-min-area-m2`) with low gradient to its parent. If spacing is wide, confidence is lowered. No dense slope grid is invented. |
| **POINT** | Shoreline vertex prominence ≥ `point-min-prominence-m` **and** prominence/window ≥ `point-min-prominence-ratio` (rejects ordinary corners). Candidates must jut toward the lake centroid. Nearby bathymetry raises confidence. Min spacing applies. |
| **ISLAND_EDGE** | Island geometry above `island-min-area-m2`. Geometry-only islands get lower confidence; bathymetry within `island-bathy-buffer-m` raises it. |

`FeatureConfidenceService` is the only place that outputs 0–1 confidence. Extractors supply evidence (prominence, bathymetry support, geometry consistency, interpolation distance, supporting data types). The service also uses lake-level source quality (contour count, point count, index-only, shoreline/islands, mean contour spacing).

## Derived artifacts

`derived_analysis_artifacts` always stores algorithm version, parameters, source snapshot, grid/tile metadata, and SHA-256 checksum.

`app.processing.persist-surfaces` (default `false`) controls whether a JSON sidecar is written under `app.processing.derived-dir`. Surface/tile blobs are not written in the contour path. Grid metadata records `wholeLakeRaster=false`.

## Admin APIs

Gated by `app.admin.enabled=true` (dev/test).

| Method | Path |
| --- | --- |
| POST | `/api/v1/admin/lakes/{lakeId}/process?pipeline=GIS\|VISION\|HYBRID` (default GIS) |
| POST | `/api/v1/admin/lakes/{lakeId}/benchmark` |
| GET | `/api/v1/admin/lakes/{lakeId}/benchmark` |
| GET | `/api/v1/admin/lakes/benchmark-summary?ids=` |
| GET | `/api/v1/admin/lakes/{lakeId}/features?pipeline=` |
| GET | `/api/v1/admin/lakes/{lakeId}/features.geojson?pipeline=&type=&minConfidence=&analysisVersion=` |
| GET | `/api/v1/admin/lakes/{lakeId}/map.png` |
| GET | `/api/v1/admin/lakes/{lakeId}/data-summary` (Phase 2 datasets **plus** `analysis` quality fields) |
| GET | `/api/v1/admin/lakes/data-summary?ids=` |

```bash
curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/process
curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  "http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/process?pipeline=HYBRID"
curl -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  "http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/features.geojson?pipeline=GIS"
```

`process?pipeline=HYBRID` refreshes GIS and VISION only when `StructurePipelineReadinessService` says they are not product-ready (missing, failed, empty, stale, or provenance-invalid). It then **pins** `gisParentRunId`, `gisAnalysisVersion`, `visionParentRunId`, `visionAnalysisVersion`, and `sourceSnapshotId` on the Hybrid analysis run **before** merge. Hybrid feature reads use those snapshots only. GIS and VISION parents must share the same canonical Phase 2 `sourceSnapshotId`. Legacy Hybrid rows without those pins are not product-ready (`PROVENANCE_INVALID`). Generate Plan never calls process.

Readiness fingerprinting uses dataset status plus geometry **counts / import versions / boundary identity**, not a full contour load. GIS/VISION/HYBRID replace keeps `lake_features` rows still referenced by `trip_waypoints` so historical plans stay immutable.

Authenticated `GET /api/v1/lakes/{id}/planning-capabilities` reports GIS and HYBRID availability only (no processing, no VISION option).

Live Head / Rice / Scugog / Simcoe GIS, VISION, and HYBRID counts, parent pins, Vision tile/request counts, and cascade durations: [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md). Do not treat fixture lakes as that report.

## Four-lake quality report

`data-summary` `analysis` includes more than counts:

- bathymetry index/line/point status
- contour count and bathymetry point count
- processing duration
- feature count by type
- average confidence and 0.25-wide distribution buckets
- `NOT_AVAILABLE` / `FAILED` feature types
- warnings / data-quality limitations (index-only bathy, FLAT without slope raster, geometry-only islands, retained last-good sets)

Fixture tests exercise Head (full structure), Rice (shoreline only → bathymetry types `NOT_AVAILABLE`), Scugog (contours only → POINT/ISLAND_EDGE `NOT_AVAILABLE`), and Simcoe (full). Live Ontario counts are not invented here; see [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md) for the local-live GIS/VISION/HYBRID cascade.

## Known gaps

- Not Garmin-grade bathymetry; closed-contour nesting misses open-line-only structure.
- No TIN / PostGIS Raster interpolation yet. FLAT/DROP_OFF will under-detect on sparse contours rather than invent a dense grid.
- POINT is conservative and will miss low-prominence points.
- `HOLE` is intentionally not stored; splitting BASIN later needs explicit area/relief thresholds and a doc change.
- No Planning JSON, ranking, routes, GPS, or feedback. Canonical-render Vision uses OpenAI only for `pipeline=VISION` (never during `mvn test`).
- Phase 4 fishing strategy consumes this structure as summaries only (`app.strategy.feature-pipeline`, default GIS). See [fishing-strategy.md](fishing-strategy.md). Production structure input remains GIS until Hybrid is accepted.
- Direct Screenshot Vision is an external metrics file, not an API.

## Tests

Synthetic contours/shoreline in `LakeProcessingIT` / `LakeProcessingPartialIT` / `StructureBenchmarkIT`. No live LIO or OpenAI in `mvn test`. `@Tag("live")` stays excluded.
