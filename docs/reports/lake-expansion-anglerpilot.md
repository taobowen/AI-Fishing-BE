# Lake expansion + AnglerPilot

- generatedAt: `2026-09-18`
- environment: **prod** (`https://api.onwaterguide.taobowen.com`), worker image `lakes16-workerfix` (task def rev 5)
- scope: 16 new lakes IMPORT (done) + GIS PROCESS (running) after catalog deploy; existing Head / Rice / Scugog / Simcoe not re-imported
- overall: **16-LAKE IMPORT+PROCESS COMPLETE; NOT FULLY PRODUCT-READY**

Product-ready still requires **GIS PROCESS `READY`/`PARTIAL` and `SNAPSHOT_READY`**. A catalog row is not enough.

A catalog row is **not** product-ready. The four columns below are independent. Do not collapse them.

| Column | Meaning | How it becomes true |
| --- | --- | --- |
| `CATALOG_READY` | Stub row + slug + centroid + `card_image_path` | `POST /api/v1/admin/lakes/validation-catalog` (or `DevDataInitializer`) |
| `IDENTITY_VERIFIED` | IMPORT resolved official OHN identity / `ogfId` | Live `POST .../import` (not name-only) |
| `GIS_READY` | PROCESS GIS analysis `READY` or `PARTIAL` | Live `POST .../process?pipeline=GIS` |
| `SNAPSHOT_READY` | `SpatialPlanningSnapshot` built by the analysis-ready hook | Automatic after GIS/HYBRID `READY`/`PARTIAL` (`SpatialSnapshotTrigger.afterAnalysis`). Script does **not** POST SNAPSHOT. |

`sourceSnapshotId` on a GIS/VISION/HYBRID analysis run is a Phase 2 structure fingerprint. It is **not** `SNAPSHOT_READY`.

## Per-lake status

Existing four: identity and GIS from earlier live reports ([production-data-validation.md](production-data-validation.md), [four-lake-hybrid-planning.md](four-lake-hybrid-planning.md)). Not re-imported in this run.

New sixteen: prod IMPORT `SUCCEEDED` on `2026-09-18` (workerfix). GIS PROCESS jobs enqueued the same day; snapshot still follows the analysis-ready hook.

| Lake | UUID | Slug | Centroid | CATALOG_READY | IDENTITY_VERIFIED | GIS_READY | SNAPSHOT_READY |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Head Lake | `44444444-4444-4444-4444-444444444444` | `head` | 44.75, -78.92 | true | true (`ogfId` 551154028) | true (GIS READY) | not proven after 8.9.1 hook |
| Rice Lake | `44444444-4444-4444-4444-444444444445` | `rice` | 44.18, -78.17 | true | true (`ogfId` 124215626) | true (GIS READY) | true (READY snapshot `1fa26376-3ba4-43ad-b6a8-5b286fb67738`, 2026-09-05; later bbox rebuild requested) |
| Lake Scugog | `44444444-4444-4444-4444-444444444446` | `scugog` | 44.15, -78.90 | true | true (`ogfId` 260759954) | true (GIS READY) | not proven after 8.9.1 hook |
| Lake Simcoe | `44444444-4444-4444-4444-444444444447` | `simcoe` | 44.42, -79.37 | true | true (`ogfId` 851703532) | true (GIS READY) | true (later Generate Plan + live snapshot `777a0073-7a01-4988-978a-ede4946d3cf0`) |
| Balsam Lake | `44444444-4444-4444-4444-444444444448` | `balsam` | 44.58, -78.85 | true | true (`ogfId` 1252543749, Balsam Lake) | true (GIS READY) | true (`e6191616-e293-4008-a81b-641188397521`) |
| Pigeon Lake | `44444444-4444-4444-4444-444444444449` | `pigeon` | 44.50, -78.50 | true | true (`ogfId` 1253437644, Pigeon Lake) | true (GIS READY) | true (`fc083826-7b04-4431-bf19-ca6459247585`) |
| Sturgeon Lake | `44444444-4444-4444-4444-444444444450` | `sturgeon` | 44.47, -78.73 | true | true (`ogfId` 1253432451, Sturgeon Lake) | true (GIS READY) | true (`ef9e2862-9ebf-4071-a843-9602d88bd262`) |
| Lake Couchiching | `44444444-4444-4444-4444-444444444451` | `couchiching` | 44.67, -79.38 | true | true (`ogfId` 70653509, Lake Couchiching) | true (GIS READY) | true (`b3bd514c-3a7b-4503-87ad-e59326ff59a1`) |
| Canal Lake | `44444444-4444-4444-4444-444444444452` | `canal` | 44.56, -79.05 | true | true (`ogfId` 1251066762, Canal Lake) | true (GIS READY) | true (`af4f9667-56a0-456f-b307-25ddb49dd774`) |
| Sparrow Lake | `44444444-4444-4444-4444-444444444453` | `sparrow` | 44.82, -79.40 | true | true (`ogfId` 114307154, Sparrow Lake) | true (GIS READY) | true (`ef4420e8-c424-44ef-8257-b4a73d5196a8`) |
| Lake Wilcox | `44444444-4444-4444-4444-444444444454` | `wilcox` | 43.95, -79.44 | true | true (`ogfId` 127162680, Wilcox Lake) | true (GIS READY) | true (`984389c0-dc76-46cb-9012-6e53714ac3f0`) |
| Musselman's Lake | `44444444-4444-4444-4444-444444444455` | `musselmans` | 44.02, -79.27 | true | true (`ogfId` 1200800558, Musselman Lake) | true (GIS READY) | true (`4516113c-ae73-4367-a09e-d3cd8787ba60`) |
| Preston Lake | `44444444-4444-4444-4444-444444444456` | `preston` | 44.03, -79.37 | true | true (`ogfId` 127163150, Preston Lake) | true (GIS READY) | true (`fb7dad1e-47d0-4b72-8fed-f1b97f1371ad`) |
| Heart Lake | `44444444-4444-4444-4444-444444444457` | `heart` | 43.74, -79.79 | true | true (`ogfId` 127163933, Heart Lake) | true (GIS READY) | true (`4aab2239-87ee-496b-8a62-f3e1ef6edf40`) |
| Professor's Lake | `44444444-4444-4444-4444-444444444458` | `professors` | 43.75, -79.75 | true | **false** (IMPORT SUCCEEDED, unique OHN resolve failed) | false (analysis FAILED; job still SUCCEEDED; no snapshot) | false |
| Island Lake Reservoir | `44444444-4444-4444-4444-444444444459` | `island-reservoir` | 43.93, -80.07 | true | true (`ogfId` 851206721, Island Lake) | true (GIS READY) | true (`20988ced-04de-4346-8766-38c6dc8fce33`) |
| Belwood Lake | `44444444-4444-4444-4444-444444444460` | `belwood` | 43.78, -80.33 | true | true (`ogfId` 127431693, Lake Belwood) | true (GIS READY) | true (`cfd1f0f3-89e5-4071-a444-ee535feeb166`) |
| Mountsberg Reservoir | `44444444-4444-4444-4444-444444444461` | `mountsberg` | 43.45, -80.03 | true | true (`ogfId` 167698346, Mountsberg Reservoir) | true (GIS READY; no LIO bathy) | not in PROCESS result |
| Christie Lake | `44444444-4444-4444-4444-444444444462` | `christie` | 43.28, -80.02 | true | true (`ogfId` 155931008, Christie Reservoir) | true (GIS READY; no LIO bathy) | true (`70519f5e-cdfa-4b37-8211-61b70406dd41`) |
| Buckhorn Lake | `44444444-4444-4444-4444-444444444463` | `buckhorn` | 44.48, -78.38 | true | true (`ogfId` 1253432521, Buckhorn Lake) | true (GIS READY) | true (`5eb28de9-be12-4c65-81a4-3475331007ef`) |

Counts: **20 / 20** `CATALOG_READY`. **19 / 20** `IDENTITY_VERIFIED`. **19 / 20** `GIS_READY` (Professor's Lake analysis FAILED). **16 / 20** `SNAPSHOT_READY` with an id (Rice, Simcoe, plus 14 new lakes). Mountsberg GIS READY but no snapshot id on the PROCESS result. Head / Scugog snapshot still not proven after the 8.9.1 hook.

## What changed

### Catalog

Source of truth remains code + `lakes` table. `ValidationCatalogService.VALIDATION_LAKES` is 20 Ontario stubs (`CatalogLake(id, name, slug, lat, lng)`). `cardImagePath()` is `lakes/{slug}.jpg`. Stub insert writes `province=Ontario`, `country=Canada`, centroid, `America/Toronto`, `source=MANUAL_SEED`. Existing rows get `card_image_path` backfilled if blank.

IDs live on `DevSeedIds` (`44444444-4444-4444-4444-444444444444` … `463`). `DevDataInitializer` seeds the same 20. Flyway `V50__lake_card_image_path.sql` adds the column and backfills Head / Rice / Scugog / Simcoe. `scripts/validation-scenarios.json` lists all 20 IDs.

`POST /api/v1/admin/lakes/validation-catalog` is idempotent: insert missing stubs, backfill blank paths, no reset.

### Queue-style bootstrap

`scripts/bootstrap-validation-lakes.sh` `all` is queue-parallel, not lake-serial:

1. `POST /api/v1/admin/lakes/validation-catalog` once.
2. Enqueue **IMPORT** for every selected lake first (record `jobId`s). Do not wait for PROCESS/SNAPSHOT of A before submitting B’s IMPORT.
3. Poll all IMPORT jobs (`GET /api/v1/admin/lakes/jobs/{id}`).
4. As each IMPORT reaches `SUCCEEDED`, enqueue that lake’s PROCESS (`?pipeline=GIS`).
5. Do **not** submit SNAPSHOT from the script. `SpatialSnapshotTrigger.afterAnalysis` builds the spatial snapshot when GIS/HYBRID analysis is `READY` or `PARTIAL`. `LakeOpsJobExecutor` does not mark PROCESS `SUCCEEDED` until that snapshot is `READY` (VISION skips snapshot).
6. `--resume`, enqueue dedupe, and the state file under `build/validation-artifacts/` are unchanged.

Worker concurrency: `InlineLakeOpsJobLauncher` detaches `claimAndRun` onto a bounded in-process executor (**concurrency 1**, queue 64) so POST returns 202 immediately. That 1-thread pool does **not** apply when `app.ops.jobs.launcher=ecs`. Production uses `EcsLakeOpsJobLauncher` + `app.ops.jobs.ecs.max-concurrent` (prod **2**): POST still inserts `QUEUED` and returns 202; `RunTask` only if occupied slots (`QUEUED|RUNNING` with `launchAttemptedAt` or `ecsTaskArn`) are below the cap. Extra jobs stay `QUEUED` without an ARN (slot wait). `LakeOpsReconciler` launches the oldest waiter when a slot frees.

Prod operational notes (`2026-09-18`): first IMPORT wave failed because worker tasks crashed on `ProdSecurityConfig` / non-web JVM (`lakes16-20260918`). `lakes16-workerfix` (rev 5) starts. Retry IMPORT: all 16 `SUCCEEDED`. Bootstrap then died twice before PROCESS: `write_job_result` used `python3 -` so stdin was the script not the job JSON; empty `pending_imports[@]` under `set -u` on resume. Both fixed. PROCESS GIS then enqueued for all 16; 16 worker tasks on `lakes16-workerfix` stayed up. Script poller is `--resume --skip-plan`. Token refresh must be kept alive for the multi-hour GIS run.

## 2026-09-18 incident (worker path)

GIS itself was not the delay. Once `lakes16-workerfix` stayed up, 16 IMPORTs took minutes and 16 PROCESS jobs finished in ~9 minutes. Hours went to getting a worker that could start, then recovering from a script that treated HTTP `SUCCEEDED` as the only gate.

What burned time:

1. **Catalog lives in the deployed JAR.** New lakes in `ValidationCatalogService.VALIDATION_LAKES` do not exist on prod until a new image is live. That forced a full build/push/CDK cycle before any IMPORT. **Moving catalog off JAR constants is backlog only** — not fixed in this incident.
2. **Image arch mismatch.** CDK worker task is `CpuArchitecture.X86_64`. Unpinned local `docker build` on Apple Silicon produced `lakes16-20260918` that Fargate could not pull (`CannotPullContainerError`). Dockerfile + CI + runbook now pin `linux/amd64` (`--provenance=false`). Crane is the documented fallback when Docker Desktop `push` hangs.
3. **Worker JVM never smoke-tested.** `SPRING_PROFILES_ACTIVE=prod,worker` + `web-application-type: none` loaded `ProdSecurityConfig` / `HttpSecurity`. All 16 first-wave IMPORT jobs `FAILED`. `SecurityConfig` is now `@Profile("!worker")` + servlet-only. Paired tests: worker context has **no** servlet `SecurityFilterChain`; prod API still loads the JWT / `ADMIN` chain.
4. **ECS concurrency was unbounded.** Sixteen admin POSTs launched sixteen 1 vCPU / 4 GiB Fargate tasks. Inline `concurrency=1` never applied. Cap is now `app.ops.jobs.ecs.max-concurrent` (prod 2). Tests prove 16 enqueues launch exactly N tasks.
5. **Jobs could sit QUEUED/RUNNING forever.** Reconciler already failed STOPPED tasks and `QUEUED` with no ARN after `launch-stale` on `createdAt` (which would false-fail slot-wait). It now: fails launch-stale only after `launchAttemptedAt`; leaves slot-wait `QUEUED`; fails `describe` empty (`WORKER_START_FAILED`); fails stale `RUNNING` heartbeat (`JOB_TIMEOUT`, `running-stale` 15m). STOPPED → `WORKER_START_FAILED`.
6. **`SUCCEEDED` was a lie.** IMPORT could succeed with `identityResolved=false` (Professor's). PROCESS could succeed when GIS `FAILED` or `buildIfReady` returned null (Mountsberg). Fail-closed now: IMPORT needs identity + `ogfId`; GIS/HYBRID PROCESS needs READY/PARTIAL + snapshot id; VISION is snapshot-exempt. Responses include `failureCode` + `failureMessage`. Bootstrap gates on those fields, never message text. Subset `LAKE_IDS` writes `docs/reports/lake-ops-{env}-{runId}.md` and must not overwrite `production-data-validation.md`.

Machine-readable codes: `IDENTITY_RESOLUTION_FAILED`, `GIS_PROCESSING_FAILED`, `NO_PERSISTED_FEATURES`, `SPATIAL_SNAPSHOT_FAILED`, `WORKER_START_FAILED`, `JOB_TIMEOUT`.

### Card images: path in DB, URL on list APIs

| Layer | Field | Example |
| --- | --- | --- |
| DB | `lakes.card_image_path` | `lakes/simcoe.jpg` (identity/path, not an environment URL) |
| API (lake pickers) | `cardImageUrl` on `LakeSummaryResponse` / `LakeResponse` | `{PUBLIC_ASSET_BASE_URL}/lakes/simcoe.jpg` |
| API (cards) | `lakeCardImageUrl` on `TripResponse`, `PastTripResponse`, `UserPlanSummaryResponse` | same resolution, batch-mapped on the list |

Resolver: `LakeCardImageResolver` joins `app.public-asset-base-url` (`PUBLIC_ASSET_BASE_URL`; empty → root-relative `/lakes/...`). Missing path or missing classpath file → `lakes/_placeholder.jpg`. List mappers (`TripService`, `PastTripQueryService`, `UserPlanQueryService`) batch-resolve one lake lookup per list. Cards must not call `GET /lakes/{id}` per row.

Static v1: `src/main/resources/static/lakes/{slug}.jpg` for all 20 catalog lakes plus `_placeholder.jpg`.

### FE / WEB card consumers

Backend list DTOs and vendored FE OpenAPI (`TripResponse`, `PastTripResponse`, `UserPlanSummaryResponse`) expose `lakeCardImageUrl`. Lake pickers expose `cardImageUrl`.

UI now renders `lakeCardImageUrl` from the list APIs those surfaces already call:

- RN Plans (`listTrips()` → `GET /api/v1/trips`) renders `item.lakeCardImageUrl` from `TripResponse`.
- RN Past Trips (`listPastTrips()`) renders `trip.lakeCardImageUrl` from `PastTripResponse`.
- WEB My Plans (`listMyPlans()` → `GET /api/v1/me/plans`) renders `card.lakeCardImageUrl` from `UserPlanSummary`.

A missing URL still uses a muted placeholder. The API already resolves missing files to `lakes/_placeholder.jpg`. Cards must not call `GET /lakes/{id}` per row.

### AnglerPilot public rebrand

User-facing copy: `Onwater Guide` / `OnwaterGuide` / `onwaterguide` → **AnglerPilot**. Legal operator stays Castwise Fishing Inc.

Updated in product surfaces: `AI-Fishing-WEB/src/content/site.ts`, FE `app.json` + `brand.ts` + permission strings, `plans.tsx` / `gps.ts` title via `brand.name`, `legal.ts` via `brand.siteUrl`, Cognito email templates.

Left unchanged (internal): Java packages `com.aifishing`, bundle / Android `com.aifishing.app`, scheme `aifishing://`, Cognito prefix `castwise-taobowen`, CDK stack names `AiFishing-*`.

## Identity resolver (units as in code)

`LakeIdentityResolver` is **not** name-only.

1. Prefer existing `ogfId`, else parse `sourceLakeId` as `OGF_ID`. Known id → `LakeQuerySupport.byOgfId`.
2. Else bbox around centroid: `LakeQuerySupport.IDENTITY_PAD_DEG` = **0.05 degrees** (not km). Comment in code: a 0.2° window returns thousands of OHN waterbodies.
3. Unique match: centroid inside polygon, else official-name match (`OFFICIAL_NAME_LABEL`, `OFFICIAL_NAME`, `GEOGRAPHIC_NAME`, `NAME`, `OFFICIAL_WATERBODY_NAME`). Name normalize strips `lake` and punctuation.
4. Named-match distance cap: `app.ontario.identity-max-distance-km` = **15** (`OntarioProperties.identityMaxDistanceKm`).
5. Watercourses (river/stream/creek) are excluded unless they contain the centroid. Non-containing matches must be lake/pond/reservoir type.
6. `closestUnique` sorts remaining candidates by `distanceKm()` (haversine km). If two remain and `Math.abs(best.distanceKm() - second) < 0.05`, it throws ambiguous. That **0.05 is kilometers** because it compares `distanceKm()`. It is not a separate product “0.05 km ambiguity guard,” and it is not `IDENTITY_PAD_DEG`.

IMPORT applies `ogfId`, `officialName`, municipality, `waterbodyLid`, boundary, bbox, and `identityMetadata`.

## High-risk names

Centroids are inside the intended waterbody. Still expect IMPORT identity failures or wrong-lake matches if LIO returns near-duplicates.

| Lake | Intended | Collision risk |
| --- | --- | --- |
| Canal Lake | Trent-Severn, 44.56, -79.05 | Name contains “Canal”; watercourse exclusion helps only if type is river/stream/creek. Must not resolve a canal **line**. |
| Heart Lake | **Brampton**, 43.74, -79.79 | Other Ontario “Heart” waterbodies. Centroid + 15 km named-match cap is the guard. |
| Christie Lake | **Christie Lake CA / Hamilton**, 43.28, -80.02 | Must not resolve Frontenac Christie Lake. |
| Sturgeon Lake | **Kawartha**, 44.47, -78.73 | Must not resolve Nipissing / other Sturgeon lakes. |
| Preston Lake | Whitchurch-Stouffville, 44.03, -79.37 | Small kettle; nearby Wilcox / Musselman's / other York Region ponds. |

If two named polygons sit at nearly the same centroid distance, `closestUnique` throws when `|d1 − d2| < 0.05` km. Operators should set `ogfId` / `sourceLakeId` on the stub and re-IMPORT rather than widening pads.

## Image path vs URL, and how to add another lake

**Path vs URL.** Persist `card_image_path` (`lakes/{slug}.jpg`). Resolve at read time with one `PUBLIC_ASSET_BASE_URL`. Do not store `https://onwaterguide...` or `https://anglerpilot...` in the row.

**Add a 21st lake later (no bulk admin API):**

1. Add `DevSeedIds.SOME_LAKE_ID` (`44444444-4444-4444-4444-444444444464` or next).
2. Append `CatalogLake` to `VALIDATION_LAKES` (official name, slug, centroid **inside** the waterbody).
3. Add the UUID + default species to `scripts/validation-scenarios.json`.
4. Drop `src/main/resources/static/lakes/{slug}.jpg` (16:9). Missing file → shared `_placeholder.jpg`. No per-component photo tables.
5. Deploy / migrate. `POST /api/v1/admin/lakes/validation-catalog` inserts the stub + path.
6. Operational: enqueue IMPORT (all new lakes first), poll, enqueue PROCESS GIS as each IMPORT succeeds. Snapshot is the analysis-ready hook. `--resume` on the bootstrap script.
7. Confirm `IDENTITY_VERIFIED` / `GIS_READY` / `SNAPSHOT_READY` separately. Do not ship Generate Plan until `SNAPSHOT_READY`.

## Domain / CORS / Cognito / EAS leftovers

Rebrand made these **config-ready**. Live `onwaterguide.*` is **not** cut over. DNS / CDK deploy remains operational follow-up.

| Surface | Config-ready (AnglerPilot) | Still live / leftover `onwaterguide.*` |
| --- | --- | --- |
| WEB canonical | `NEXT_PUBLIC_SITE_URL` default `https://anglerpilot.taobowen.com` (`site.ts`, `.env.example`) | README still documents live `https://onwaterguide.taobowen.com` → `https://api.onwaterguide.taobowen.com` |
| CORS | `CorsProperties` + `APP_CORS_ALLOWED_ORIGIN_PATTERNS` default includes `https://anglerpilot.taobowen.com` | Live site origin still `onwaterguide` until cutover; add both during overlap |
| Cognito / CDK | `nextWebDomain` default `anglerpilot.taobowen.com`; `withNextWebAuthUrl` appends callback/logout | `cdk.json` / `config.ts` live `apiDomain` / `webDomain` still `api.onwaterguide.taobowen.com` / `onwaterguide.taobowen.com`; callback/logout lists still include live `onwaterguide` + `castwise` |
| EAS | `EXPO_PUBLIC_SITE_URL` documented default `https://anglerpilot.taobowen.com` (`brand.ts`, FE README) | `eas.json` preview/production still `EXPO_PUBLIC_API_BASE` / `EXPO_PUBLIC_SITE_URL` = `onwaterguide.*` |
| Docs | — | `docs/production-deployment.md`, `docs/web-mvp.md`, FE `UI-UX-CONTEXT.md`, `FIGMA-IMPLEMENTATION-MAPPING.md`, root architecture markdown still say Onwater Guide / `onwaterguide.*` |

Cognito domain prefix `castwise-taobowen` is intentional (not a public product string).

## Unresolved identity risk list

Observed on prod IMPORT `2026-09-18` (workerfix). Highest remaining risk first:

1. **Professor's Lake** — IMPORT `SUCCEEDED` but `identityResolved=false`: unique OHN resolve failed (`centroid + official name`). No `ogfId`. PROCESS job `SUCCEEDED` with `processingStatus=FAILED`, `contourCount=0`, all six feature types `notAvailable`, no spatial snapshot. Set `ogfId` / `sourceLakeId` and re-IMPORT, then re-PROCESS.
2. **Christie** — resolved `Christie Reservoir` `ogfId` 155931008 (Hamilton CA / reservoir label, not Frontenac Christie Lake). Accept unless boundary review disagrees.
3. **Sturgeon** — resolved `Sturgeon Lake` `ogfId` 1253432451 from Kawartha centroid 44.47, -78.73. Treat as Kawartha unless a later boundary review says otherwise.
4. **Heart** — resolved `Heart Lake` `ogfId` 127163933 from Brampton centroid 43.74, -79.79.
5. **Canal** — resolved `Canal Lake` `ogfId` 1251066762 (named lake, not a linear canal).
6. **Island / Belwood / Musselman's** — official labels differ from catalog (`Island Lake`, `Lake Belwood`, `Musselman Lake`). IDs accepted.
7. **Preston / Pigeon / Balsam / Buckhorn / Couchiching / Sparrow / Wilcox / Mountsberg** — unique resolve succeeded; no collision thrown.

On Professor's (and any later collision): set `ogfId` (or numeric `sourceLakeId`) on the catalog row and re-IMPORT via `byOgfId`. Do not widen `IDENTITY_PAD_DEG` as a first fix.

## Next operational run (out of scope here)

```bash
cd AI-Fishing-BE
export API_BASE=...
export ENVIRONMENT=local-live   # or prod + ADMIN_BEARER / USER_BEARER
./scripts/bootstrap-validation-lakes.sh
# or --lake-id= for a subset; --resume after interrupt
```

Validate per lake: `ogfId` + official name (identity), GIS `READY`/`PARTIAL` (process), `spatialSnapshotStatus=READY` on the PROCESS job result (snapshot hook). Then Generate Plan. Do not mark a lake ready from the catalog stub alone.
