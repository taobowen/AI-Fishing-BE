# Structure quality benchmark (Phase 3.1)

Phase 3.1 proves lake structure is good enough to feed later Fishing Strategy / Planning. It does **not** add fishing logic, ranking, weather, GPS, routes, or feedback.

Production default remains **GIS** until Hybrid is accepted. `lakes.processing_status` is always the GIS summary. Product Generate Plan: **Standard** = GIS; **AI-Enhanced (Experimental)** = HYBRID. VISION-only is internal processing. Direct Screenshot Vision is an external/manual benchmark only. Hybrid product readiness requires pinned GIS/VISION parent run ids + analysis versions and a matching `sourceSnapshotId`. Observed four-lake Hybrid cascade: [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md).

## Pipelines

| Code | Pipeline | In backend? |
| --- | --- | --- |
| **A** | Deterministic GIS extractors (HUMP, DROP_OFF, FLAT, POINT, BASIN, ISLAND_EDGE) | Yes. `POST .../process` default. |
| **B** | Canonical-render Vision | Yes. Georeferenced map from **our** PostGIS vectors → GPT Vision → pixel JSON → EPSG:4326. `pipeline=VISION`. |
| **C** | Hybrid | Yes. Canonical-render Vision candidates + GIS, with deterministic geospatial validation. `pipeline=HYBRID`. |
| External | Direct Screenshot Vision | **No.** Manual benchmark reference only. |

There is no `SCREENSHOT` pipeline and no screenshot upload, georeferencing, or `lake_features` persistence for screenshot output.

Same thresholds (`app.processing.*`) for every lake. No Head / Rice / Scugog / Simcoe branches.

```text
canonical PostGIS → GIS extractors ──┐
                 ↘ map.png + georef.json → GPT Vision → VISION ─┬→ Hybrid validate/merge
                                                                └→ benchmark report
Head human overlay (GeoJSON) ────────────────────────────────────→ Head P/R/F1
docs/benchmarks/head-direct-screenshot-vision.json ──────────────→ external baseline section
```

## Map render and georef

`BathymetricMapRenderer` paints a north-up PNG from boundary, contours (colored by depth), shoreline, islands, plus a small legend/scale. Large lakes tile the bbox with overlap (`app.processing.tile-size-m`, `app.vision.max-image-px`, `app.vision.tile-overlap-m`).

Pixel (0,0) is the northwest corner of the rendering bbox. Transform is a linear affine in EPSG:4326:

- `lng = minLng + x / (widthPx - 1) * (maxLng - minLng)`
- `lat = maxLat - y / (heightPx - 1) * (maxLat - minLat)`

Sidecars: `{derivedDir}/{lakeId}/{analysisVersion}/map.png` and `georef.json` when `app.vision.persist-maps` is true (default).

`GET /api/v1/admin/lakes/{id}/map.png` returns the latest persisted canonical render, or a live render if none is stored.

## Canonical-render Vision prompt contract

`app.openai.api-key` from `OPENAI_API_KEY`. Default model `gpt-4o`.

The prompt asks only for the six feature types, **pixel** coordinates, optional 0–1 self-score, and a short evidence string. It must not ask for fishing advice.

Expected JSON:

```json
{"features":[{"type":"HUMP","geometryType":"Polygon","coordinates":[[[x,y],...]],"confidence":0.8,"evidence":"closed shallow contour"}]}
```

`DROP_OFF` is a LineString. `POINT` is `[x,y]`. Parse failures mark that Vision type `FAILED` and keep the last good Vision set. `mvn test` never calls OpenAI; tests mock `VisionMapClient`. Optional `@Tag("live")` smoke is excluded from Surefire.

Overlapping tiles are deduplicated by type + IoU before persist.

## Hybrid validation

Starts from canonical-render Vision, not a second LLM and not screenshot Vision:

- Must intersect the lake interior (existing `FeatureFactory` clip).
- HUMP / BASIN: nested-contour or local relief using GIS thresholds.
- DROP_OFF: tight contour spacing / gradient.
- FLAT: min area + low gradient.
- POINT: near shoreline.
- ISLAND_EDGE: intersects island geometry.
- Snap Vision polygons to a supporting closed contour when IoU ≥ 0.3.
- Reject out-of-lake, tiny, or unsupported candidates.
- Add unmatched GIS features with confidence ≥ `app.vision.hybrid-gis-min-confidence` (default 0.6).
- Confidence still comes only from `FeatureConfidenceService` (Vision support is extra evidence).

Every new Hybrid analysis run persists required parent pins (`gisParentRunId`, `gisAnalysisVersion`, `visionParentRunId`, `visionAnalysisVersion`) plus a shared Phase 2 `sourceSnapshotId`. Merge loads those exact GIS/VISION snapshots. Incompatible source fingerprints are refused. Legacy Hybrid rows without pins are `PROVENANCE_INVALID` and are not product-ready.

## Head Lake human overlay

File: [`docs/benchmarks/head-lake-reference.geojson`](benchmarks/head-lake-reference.geojson) (classpath copy under `src/main/resources/benchmarks/`).

### Labeling SOP

1. After Phase 2 admin import of Head Lake, render `GET .../map.png`.
2. Label only **clearly identifiable major** humps, drop-offs, flats, points, basins, and island edges.
3. Target 20–40 features. Same six types, EPSG:4326.
4. Properties: `type`, optional `name` / `notes`, `confidence=high`.
5. Do **not** copy GIS or Vision output. The overlay is an independent referee.
6. The committed file may be an empty FeatureCollection until that visual review is done. Tests use a synthetic gold set, not live Head labels.

## Scoring

Matcher requires the same `FeatureType`:

| Geometry | Match |
| --- | --- |
| Polygon | IoU ≥ 0.3; report mean IoU of matches |
| LineString | 40 m buffer then IoU, or centroid/Hausdorff ≤ 75 m |
| Point | distance ≤ 75 m |

Head vs gold (when the overlay has features **and** `properties.lakeName` matches the lake): precision, recall, F1, matched IoU / location error, false positives, missed majors.

All four lakes also report GIS vs canonical-render Vision agreement: matched / GIS-only / Vision-only, plus in-lake %, duration, and warnings.

Rice / Scugog / Simcoe have no full gold sets.

## External Direct Screenshot Vision Baseline

Not a pipeline. You run a high-quality bathymetric screenshot through a strong vision model yourself, then fill:

[`docs/benchmarks/head-direct-screenshot-vision.json`](benchmarks/head-direct-screenshot-vision.json)

| Field | Meaning |
| --- | --- |
| `model` | Model used |
| `promptVersion` | Prompt / version |
| `screenshotDescription` / `source` | What image was scored |
| `featureCountsByType` | Manually reviewed counts |
| `precision` / `recall` / `f1` | Vs Head gold, **nullable until scored** |
| `perType` | Optional per-type P/R/F1 |
| `notes` | Qualitative strengths / weaknesses |

No geometries required. `GET .../benchmark` merges this file when present. **Do not invent numbers** if scores are null.

Report section:

```text
External Direct Screenshot Vision Baseline:
- model used
- prompt/version
- screenshot description/source
- manually reviewed feature counts
- precision/recall/F1 against the Head Lake human reference where available
- qualitative strengths/weaknesses
```

## Acceptance

Document honestly if live data is too sparse (empty overlay → `acceptance.status = NOT_SCORED`).

When gold and screenshot F1 exist:

- Hybrid F1 vs Head gold ≥ **max**(canonical-render Vision F1, Direct Screenshot Vision F1)
- Hybrid recall of gold majors ≥ the stronger Vision recall
- Hybrid precision must not collapse by keeping every Vision scribble
- Target: Hybrid best among GIS, canonical-render Vision, and (when measured) screenshot Vision
- GIS vs gold is reported even if it loses — that is the gate before Planning

## Admin APIs

Gated by `app.admin.enabled`. User `GET /api/v1/lakes` still has no processing/vision details.

| Method | Path |
| --- | --- |
| POST | `/api/v1/admin/lakes/{id}/process?pipeline=GIS\|VISION\|HYBRID` (default GIS) |
| POST | `/api/v1/admin/lakes/{id}/benchmark` (run A then B then C) |
| GET | `/api/v1/admin/lakes/{id}/benchmark` |
| GET | `/api/v1/admin/lakes/benchmark-summary?ids=` |
| GET | `/api/v1/admin/lakes/{id}/features?pipeline=` |
| GET | `/api/v1/admin/lakes/{id}/features.geojson?pipeline=&type=&minConfidence=` |
| GET | `/api/v1/admin/lakes/{id}/map.png` |

## Four-lake table

| Lake | Fixture (`mvn test`) | Live Ontario |
| --- | --- | --- |
| Head | Full synthetic contours/shoreline/island; gold file empty until labeled | Import + process after Phase 2; fill overlay and screenshot JSON; do not invent counts here |
| Rice | Shoreline-only fixture; bathymetry types GIS `NOT_AVAILABLE`; Hybrid should reject unsupported bathy Vision | Same pipeline; agreement + validity only |
| Scugog | Contours only; POINT/ISLAND_EDGE GIS `NOT_AVAILABLE` | Same |
| Simcoe | Full synthetic (tiling exercised when bbox/resolution requires it) | Same; tile seams are a known gap |

Live contour counts, durations, and F1 are **not** recorded in this repo. Run admin import/process/benchmark locally after Docker PostGIS is loaded.

## Known gaps

- Vision tile seams can duplicate or split structure; IoU dedupe is a first cut.
- Ontario contour sparsity under-detects GIS structure and raises Vision hallucination risk; Hybrid should reject unsupported bathy types.
- Not Garmin-grade charts.
- Screenshot baseline is not georeferenced in-app.
- Affine georef is bbox-linear, not a DEM orthorectification.

## Tests

Synthetic only. No live LIO or OpenAI in `mvn test`. `@Tag("live")` remains excluded.
