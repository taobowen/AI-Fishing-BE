# Production data bootstrap (Phase 8.5)

Operational runbook for real Ontario import, GIS processing, and user Generate Plan on Head Lake, Rice Lake, Lake Scugog, and Lake Simcoe. This is **not** a new product feature. Ranking, extractors, and Phase 8 auth are unchanged.

Use one script against either **local-live** (docker PostGIS + live Ontario HTTP + `app.raw.storage=local`) or **prod** (ECS/RDS/S3/Cognito). Every report must set `environment`. AWS was not deployed from the original Phase 8 workspace; until it is, prod sections are `EXTERNAL PREREQUISITE`.

## Prerequisites

- Backend running (`dev` profile locally, or `prod` on ECS)
- `API_BASE` origin (no trailing slash)
- Admin auth:
  - local-live: `ADMIN_USER_ID` (`X-User-Id`). `app.admin.enabled=true` grants `ROLE_ADMIN` to every resolved user, so this **cannot** prove “normal user cannot import.”
  - prod: `ADMIN_BEARER` access token for a Cognito user in group `ADMIN`. `USER_BEARER` for a user **without** that group. `X-User-Id` is forbidden.
- Live OpenAI + Open-Meteo for strategy/plan. Missing OpenAI → StrategyRun FAILED (existing behavior). Do not fall back to fixtures.
- `curl` and `python3`

Catalog UUIDs (do not replace):

| Lake | UUID |
| --- | --- |
| Head Lake | `44444444-4444-4444-4444-444444444444` |
| Rice Lake | `44444444-4444-4444-4444-444444444445` |
| Lake Scugog | `44444444-4444-4444-4444-444444444446` |
| Lake Simcoe | `44444444-4444-4444-4444-444444444447` |

`POST /api/v1/admin/lakes/validation-catalog` inserts missing stub rows only (name, centroid, timezone). Identity (OGF_ID, boundary) fills on import. No prod seed/reset.

## Commands

```bash
cd AI-Fishing-BE
export API_BASE=http://127.0.0.1:8080
export ENVIRONMENT=local-live
export ADMIN_USER_ID=11111111-1111-1111-1111-111111111111
# Second seeded user (dev still grants ROLE_ADMIN; this only proves the user exists)
export USER_USER_ID=11111111-1111-1111-1111-111111111112
# Required for user Generate Plan / StrategyRun. Unset → VALIDATION_ERROR, overall FAIL.
export OPENAI_API_KEY=...
chmod +x scripts/bootstrap-validation-lakes.sh

# default: live Ontario refresh for all four lakes
./scripts/bootstrap-validation-lakes.sh

# same interrupted run (state file under build/validation-artifacts/)
./scripts/bootstrap-validation-lakes.sh --resume

# intentionally reuse canonical data (does NOT use AVAILABLE as a skip signal)
./scripts/bootstrap-validation-lakes.sh --skip-import --skip-process
```

Subcommands: `bootstrap-lakes`, `import-lake`, `process-lake`, `validate-lake`, `generate-validation-trip`, `generate-validation-report`.

Prod:

```bash
export ENVIRONMENT=prod
export API_BASE=https://api.example.com
export ADMIN_BEARER=...
export USER_BEARER=...
./scripts/bootstrap-validation-lakes.sh
```

Use `--max-time` 1800 (script default). ALB idle timeout is 15 minutes on the CDK load balancer.

**AVAILABLE dataset status alone never skips import.** Only `--skip-import` or `--resume` (steps already finished in *this* run’s state file) skip a live POST.

Processing default is GIS (`app.strategy.feature-pipeline` and `app.planning.user-default-feature-pipeline`). Do **not** change the production YAML default to HYBRID. AI-Enhanced Generate Plan uses `featurePipeline=HYBRID` per request. Admin `process?pipeline=HYBRID` is operational (cascades GIS/VISION when those snapshots are not product-ready). Generate Plan never auto-processes Hybrid. Direct Screenshot Vision is not a process pipeline.

## Validation species

[`scripts/validation-scenarios.json`](../scripts/validation-scenarios.json) is **bootstrap metadata**, not lake-specific production logic. Before creating a trip the script checks that the configured species exists in canonical `lake_fish_species` **and** maps to `FishSpecies`. Otherwise it walks the explicit `sportFishFallback` list. It never picks `ORDER BY` first row. If nothing matches, plan is skipped and reported.

## Pagination fields

Dataset status includes:

- `pageCount` — ArcGIS pages stored
- `rawRecordCount` — sum of features on those pages (not `pageCount * pageSize`)
- `transferLimitObserved` — any page had `exceededTransferLimit=true` (normal on intermediate pages)
- `paginationComplete` — last page did **not** still exceed the transfer limit and the 200-page safety cap was not hit
- `paginationWarning` — only when truncated/incomplete

`transferLimitObserved=true` + `paginationComplete=true` is success.

## Artifacts

Commit [`docs/reports/production-data-validation.md`](reports/production-data-validation.md) and `.json` only. GeoJSON/PNG go to gitignored `build/validation-artifacts/{env}/{lakeId}/`. Reports store path + sha256. Visual review stays `VISUAL_REVIEW_PENDING` until a human edits the report.

## Recovery

- Retry `POST /api/v1/admin/lakes/{id}/import`. `FAILED` keeps last successful canonical rows (`lastSuccessfulImportAt` unchanged on failure).
- Retry `POST /api/v1/admin/lakes/{id}/process`. A failed extractor keeps last-good rows for that feature type.
- Retry user `POST /api/v1/trips/{id}/plan` `{}`. Creates a **new** StrategyRun; previous COMPLETED runs remain in history.
- ECS restart is safe: state is RDS + object storage. No manual SQL.

Failed-refresh safety is covered by `failedRefreshKeepsLastSuccessfulCanonical` (do not break live Ontario APIs to re-prove it).

## Auth split

- local-live: call import **without** `X-User-Id` and expect 401. User-vs-admin role split is incomplete (`CAN FIELD TEST WITH WARNING`) until Cognito.
- prod: user without `ADMIN` must 403 on `/api/v1/admin/**` and still `POST /trips/{id}/plan` with `{}`.

Assign operators to Cognito group `ADMIN` (CDK creates the empty group). Native email Hosted UI remains the fallback.

## Honest DoD

A local-live four-lake import → GIS/VISION/HYBRID process → Head Standard + AI-Enhanced Generate Plan, with an observed report, is implementation-complete in this workspace. Observed Hybrid pins, Vision tile counts, and Generate Plan outcomes: [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md). Until AWS exists, classify S3 privacy, CloudWatch, Cognito admin split, ALB/Simcoe timeouts, and HTTPS frontend as **EXTERNAL PREREQUISITE**. Do not start Phase 9 until at least Head Lake is `TECHNICALLY_VALID` on real Ontario features. `VISUAL_REVIEW_PENDING` and `FISHING_QUALITY_NOT_YET_FIELD_VALIDATED` are expected, not FAIL.

## Head Lake boat A/B/C (Phase 8.6)

After Head has usable features, run three otherwise identical Head plans that differ **only** by boat:

| Boat | Equipment |
| --- | --- |
| A | inflatable + `ELECTRIC_TROLLING` 55 lb (primary transit electric) |
| B | same hull + `GAS_OUTBOARD` 9.9 as primary transit |
| C | same hull + 15 HP gas |

Record per-metric baseline (speed / range / wind / source), candidate vs feasible counts, waypoints, farthest-from-launch, total distance, travel time, and warnings. **Do not** call one route “better fishing.” Ranking scores of the same spots should match; selection/feasibility may differ.

Template: [`docs/reports/head-lake-boat-capability-abc.md`](reports/head-lake-boat-capability-abc.md). If Head features are still thin, mark the report `FAIL` / blocked with the observed numbers.

## Head Lake launch A/B/C (Phase 8.7)

After Head has usable features **and** OpenAI for Generate Plan, run three otherwise identical Head BOAT plans that differ **only** by launch mode. Same boat, species, and day window.

| Run | Launch |
| --- | --- |
| A | `AUTO_RECOMMENDED` |
| B | `OFFICIAL_SELECTED` — a **different** known launch than AUTO picked (if two `boat_launch=true` rows exist) |
| C | `CUSTOM_SELECTED` — tap a shoreline; persist requested / shore / routeStart. Not a public ramp |

Record: `GET /lakes/{id}/boat-launches` count vs `routable` count, AUTO chosen name, official id used, CUSTOM snap distance, plan `errorMessage` if FAILED, waypoint count, route origin vs GIS access coordinate.

**Keep coverage vs unroutable distinct:**

- Zero `boat_launch=true` rows → `NO_KNOWN_BOAT_LAUNCH` (coverage). Custom picker is still allowed.
- Rows exist but none water-anchor → `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`. Do not label this as “no launches on file.”
- Do not claim a custom shoreline is legal access.

Template: [`docs/reports/head-lake-launch-selection-abc.md`](reports/head-lake-launch-selection-abc.md). Observed Head `ACCESS_POINT` pagination in the committed 8.5 report was **0 records** — that is coverage, not unroutable.
