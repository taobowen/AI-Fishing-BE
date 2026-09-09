# Four-lake Hybrid planning (observed)

- generatedAt: `2026-09-04T01:04:16Z` (Simcoe Hybrid cascade completed)
- environment: `local-live`
- apiBase: `http://127.0.0.1:8080`
- PostGIS: `127.0.0.1:5433/aifishing`
- Vision: live OpenAI GPT-4o canonical-render tiles (`response_format: json_object`). Not Direct Screenshot Vision. Not mocked.
- Generate Plan never called `process`.

Numbers below are from `lake_analysis_runs`, process JSON under `docs/reports/ops/`, and `GET /api/v1/lakes/{id}/planning-capabilities`. Feature totals are the analysis-run `feature_counts` sums. Empty / not-observed fields are stated as such.

## Summary

| Lake | GIS Status | GIS Features | VISION Status | VISION Features | HYBRID Status | HYBRID Features | HYBRID Available | Warnings |
| --- | --- | ---: | --- | ---: | --- | ---: | --- | --- |
| Head Lake | READY | 78 | READY | 1 | READY | 66 | true | FLAT contour-spacing only (GIS/HYBRID) |
| Rice Lake | READY | 240 | READY | 21 | READY | 223 | true | FLAT contour-spacing only (GIS/HYBRID) |
| Lake Scugog | READY | 7 | READY | 18 | READY | 6 | true | FLAT contour-spacing only (GIS/HYBRID); sparse bathymetry (7 contours) |
| Lake Simcoe | READY | 1067 | READY | 148 | READY | 853 | true | FLAT contour-spacing only (GIS/HYBRID); Phase 2 `BATHYMETRY_INDEX` / `FISH_SPECIES` / `WETLAND` FAILED |

Capabilities after all four Hybrid cascades (`GET .../planning-capabilities`, GIS + HYBRID only):

| Lake | GIS available | GIS analysisVersion | HYBRID available | HYBRID analysisVersion |
| --- | --- | --- | --- | --- |
| Head | true / READY | `773ec7c5-c9ad-4ca3-b9ce-5ead9f94ebac` | true / READY | `5c96e679-4211-453e-bf35-caed51e43a83` |
| Rice | true / READY | `3fa371f8-0b08-4afc-9806-1f0906fd1a70` | true / READY | `afb068b6-4752-4561-8bf2-8dd9d88d59eb` |
| Scugog | true / READY | `8603c1fe-622d-486c-a50b-307b815d7296` | true / READY | `c142f93b-c798-4bfd-8ef9-d156297092e3` |
| Simcoe | true / READY | `c4d0893f-099f-4b15-b9c7-0ea918875bdb` | true / READY | `5331e08d-5d36-46ff-a250-7d0dfa5cb030` |

VISION is not a user Generate Plan choice and is not returned by planning-capabilities.

## Durations, tiles, pins

`duration_s` is `completed_at - started_at` on the analysis run. `cascade_s` is wall-clock admin `POST .../process?pipeline=HYBRID` (GIS ensure + VISION ensure + Hybrid merge). Vision tile/request counts are from run `parameters`.

| Lake | GIS s | VISION s | HYBRID s | Cascade s | Vision tiles | Vision requests | Reused vs rerun |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Head (product snapshot) | 2 | 86 | 1 | ~89 | 28 | 28 | Provenance-forced GIS/VISION/HYBRID rerun (see Head notes) |
| Rice | 144 | 445 | 303 | 893 | 110 | 110 | GIS rerun (legacy no `sourceSnapshotId`); first product VISION + HYBRID with pins |
| Scugog | 2 | 186 | 0 | 189 | 54 | 54 | GIS rerun (legacy no `sourceSnapshotId`); first product VISION + HYBRID with pins |
| Simcoe | 905 | 1219 | 2573 | 4701 | 238 | 238 | GIS rerun (legacy no `sourceSnapshotId`); first product VISION + HYBRID with pins |

Simcoe Hybrid wall time (~43 min merge) is **not** a failure.

### Head Lake (product)

| | |
| --- | --- |
| `sourceSnapshotId` | `src-5575f7110ef1e1ffee9cae74872aae2a6bcc8e03eb20df74f4dfdb66326607bb` |
| GIS run | `80572af9-1aa2-48ab-8ee8-9450ddead755` / `773ec7c5-c9ad-4ca3-b9ce-5ead9f94ebac` / READY / 78 features (`HUMP` 12, `DROP_OFF` 35, `FLAT` 8, `BASIN` 23) |
| VISION run | `5b7b5df4-3890-4c8f-ab68-c2d458ee5217` / `0130e041-9cdb-4ea6-ace8-752c1571aa00` / READY / 1 `HUMP`; 28 tiles, 28 requests, `error_summary.messages` empty |
| HYBRID run | `8716d21b-70f9-4cee-8365-bc26bbab93c5` / `5c96e679-4211-453e-bf35-caed51e43a83` / READY / 66 features (`HUMP` 11, `DROP_OFF` 25, `FLAT` 8, `BASIN` 22) |
| HYBRID parents | GIS `80572af9` / `773ec7c5`; VISION `5b7b5df4` / `0130e041` |
| Live `lake_features` for GIS `773ec7c5` | 73 rows (5 older GIS rows `6d902914` retained because `trip_waypoints` still referenced them) |

First cascade (not product-ready; then rerun):

- GIS `f61a9564` **PARTIAL** (2 s): `HUMP`/`DROP_OFF` failed `trip_waypoints_lake_feature_id_fkey` on replace. Fixed by waypoint-safe `deleteUnreferenced`.
- VISION `630e89d4` READY (417 s): 28 tiles / 28 requests; 4 tiles not valid JSON (`r0c0`, `r2c2`, `r2c3`, `r3c3`). Fixed with `response_format: json_object` + JSON object extraction.
- HYBRID `3432315f` READY (1 s) but `HUMP` 0 / `DROP_OFF` 0 because GIS parents had failed those types. Rerun after GIS/VISION fix.

Phase 2: boundary/shoreline/bathymetry lines present. `ACCESS_POINT` AVAILABLE with **0** records. `WETLAND` FAILED.

### Rice Lake

| | |
| --- | --- |
| `sourceSnapshotId` | `src-a9d399918aebdb7a515c5b7b6765244ac6d5c6f99d3f82ea1e7f6872bb482b05` |
| GIS | `59927b8a-120a-42c6-b2dc-05d111082497` / `3fa371f8-0b08-4afc-9806-1f0906fd1a70` / READY / 240 |
| VISION | `a1139760-8258-4f89-9028-98c2dff1e5fd` / `abd61afd-ebd9-42e8-9cd3-c65674dc998e` / READY / 21; 110 / 110 |
| HYBRID | `28878ac3-85a0-4ee4-bc08-d2e7b5b4a7b4` / `afb068b6-4752-4561-8bf2-8dd9d88d59eb` / READY / 223 |
| HYBRID parents | GIS `59927b8a` / `3fa371f8`; VISION `a1139760` / `abd61afd` |
| Live GIS `3fa371f8` rows | 229 (11 older GIS `f0107633` retained via waypoints) |

Phase 2: `ACCESS_POINT` 14, `BATHYMETRY_LINE` 281. `boat_launch` / `shore_access` flags on those access points were **null** (not observed as true).

### Lake Scugog

| | |
| --- | --- |
| `sourceSnapshotId` | `src-460c8a53ee29fb83d98f22e7405af044ceb08dc07678c198d320720891d5ea94` |
| GIS | `30651565-dd9d-402a-b0e9-49d486982ad2` / `8603c1fe-622d-486c-a50b-307b815d7296` / READY / 7 (`HUMP` 6, `FLAT` 1) |
| VISION | `8664fcdf-77ef-4748-8969-3779bdecdc45` / `0f5b0460-6b0a-4c02-b0de-07479d182cae` / READY / 18; 54 / 54 |
| HYBRID | `ebc2f47c-aa79-48d5-927f-ba3022e69331` / `c142f93b-c798-4bfd-8ef9-d156297092e3` / READY / 6 (`HUMP` 5, `FLAT` 1) |
| HYBRID parents | GIS `30651565` / `8603c1fe`; VISION `8664fcdf` / `0f5b0460` |

Phase 2: `BATHYMETRY_LINE` 7, `ACCESS_POINT` 10. Sparse contours are a data limitation, not a Hybrid merge failure.

### Lake Simcoe

| | |
| --- | --- |
| `sourceSnapshotId` | `src-d2ee288e0a9ba4d2acf0703561423b0384cf3fc50585cf3b34b2368c23a397cf` |
| GIS | `8cdc2d4f-878c-45c7-849f-2db0c1553420` / `c4d0893f-099f-4b15-b9c7-0ea918875bdb` / READY / 1067 (`FLAT` 124, `HUMP` 229, `BASIN` 178, `POINT` 4, `DROP_OFF` 532) |
| VISION | `6bd66b94-465c-4544-bdb8-37a3a89245b4` / `17711ef3-8a38-4ece-b887-cc694713c6f4` / READY / 148 (`HUMP` 44, `DROP_OFF` 28, `POINT` 28, `ISLAND_EDGE` 26, `BASIN` 20, `FLAT` 2); **238 / 238** |
| HYBRID | `a58901d0-7fc0-4ad8-83a3-13f005f6cca3` / `5331e08d-5d36-46ff-a250-7d0dfa5cb030` / READY / 853 (`HUMP` 220, `DROP_OFF` 385, `FLAT` 124, `POINT` 4, `BASIN` 120) / durationMs **2572907** |
| HYBRID parents | GIS `8cdc2d4f` / `c4d0893f`; VISION `6bd66b94` / `17711ef3` |

DROP_OFF merge dominated Hybrid time (`lastAttemptedAt` 00:29:50Z → `lastSuccessfulAnalysisAt` 01:03:27Z). `failedFeatureTypes` empty. `ISLAND_EDGE` Hybrid count 0 (Vision had 26; GIS 0).

Phase 2: `BATHYMETRY_LINE` 1993, `BATHYMETRY_POINT` 39075, `ACCESS_POINT` 105. `BATHYMETRY_INDEX` FAILED, `FISH_SPECIES` FAILED, `WETLAND` FAILED (from the earlier Ontario import). GIS still READY on lines/points.

## Generate Plan smoke

### Head (SHORE trip `c53e09fc-9f84-47f3-a826-a5bf41603168`)

Ran while OpenAI strategy credits were still available. No GIS fallback on the Hybrid path.

| Request | StrategyRun | pipeline | featureAnalysisVersion | PlanningRun | Plan status |
| --- | --- | --- | --- | --- | --- |
| `{ "featurePipeline": "GIS" }` | `59527066-3f39-4b54-96a4-930332f7a2ac` COMPLETED | GIS | `773ec7c5-c9ad-4ca3-b9ce-5ead9f94ebac` | `82423fdd-0736-4595-9f3a-a8bc6c5046de` | FAILED `NO_CANDIDATES` (`SHORE_INACCESSIBLE` 9, `DUPLICATE` 55) |
| `{ "featurePipeline": "HYBRID" }` | `146ba70f-71b9-4fe0-afcc-09c9310cbaaa` COMPLETED | HYBRID | `5c96e679-4211-453e-bf35-caed51e43a83` | `760fa104-eb6a-43da-995a-61b9e11292d6` | FAILED `NO_CANDIDATES` (`SHORE_INACCESSIBLE` 5, `DUPLICATE` 2) |

Head `ACCESS_POINT` count is 0. This is a **DATA_LIMITATION**, not a pipeline or fallback bug. Strategy still bound to the exact snapshot.

### Rice / Scugog / Simcoe

Trips created `2026-09-04T01:07Z`. Both Standard and AI-Enhanced `POST /trips/{id}/plan` returned `VALIDATION_ERROR`: OpenAI Responses `credit_balance_exhausted` (429). Strategy/plan rows were not produced. Classify **EXTERNAL_PREREQUISITE**. Do not treat as Hybrid unavailability — capabilities were READY.

## Blockers

| Code | Observed |
| --- | --- |
| `DATA_LIMITATION` | Head SHORE Generate Plan `NO_CANDIDATES` / `SHORE_INACCESSIBLE` (0 access points). Scugog 7 GIS features on 7 contours. Simcoe Phase 2 index/species/wetland FAILED. Access-point `boat_launch` / `shore_access` null on Rice/Scugog/Simcoe. |
| `VISION_PROCESSING_FAILURE` | First Head Vision: 4/28 tiles invalid JSON. Product reruns: 0 JSON errors on Head/Rice/Scugog/Simcoe. |
| `HYBRID_VALIDATION_FAILURE` | Not observed on product snapshots (`failedFeatureTypes` empty). First Head Hybrid had 0 HUMP/DROP_OFF because GIS parents PARTIAL — rerun. |
| `PIPELINE_PROVENANCE_ISSUE` | Legacy GIS rows lacked `sourceSnapshotId` → forced GIS rerun on all four. First Head GIS PARTIAL from waypoint FK (code fixed; product GIS READY). |
| `PRODUCT_UI_ISSUE` | Not exercised in this local-live API run (no native app session). |
| `EXTERNAL_PREREQUISITE` | AWS/Cognito/ALB not in this run. Rice/Scugog/Simcoe Generate Plan blocked by OpenAI credit exhaustion after Vision processing. |

Raw JSON: [`docs/reports/ops/`](ops/).
