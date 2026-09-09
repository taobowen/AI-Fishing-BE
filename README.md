# AI Fishing Backend

Phase 1–8 modular monolith: Ontario lake data, structure extraction, AI strategy, trip planning, guided sessions, catch feedback, and **AWS productionization** (Cognito JWT, private S3, CDK/ECS). Production structure default remains GIS (`app.strategy.feature-pipeline` and `app.planning.user-default-feature-pipeline`). User Generate Plan may optionally request HYBRID (AI-Enhanced, Experimental). Observed four-lake Hybrid readiness: [docs/reports/four-lake-hybrid-planning.md](docs/reports/four-lake-hybrid-planning.md).

## Architecture

```text
Mobile
    │  Bearer access JWT (prod)  or  X-User-Id (dev/test)
    ▼
Spring Security + CurrentUser
    ▼
Controllers (DTOs)
    ▼
Services (ownership + validation)
    ▼
Lake ingest (admin): adapters → paginated raw → canonical PostGIS
    ▼
Lake structure (admin): GIS extractors, canonical-render Vision, Hybrid → lake_features
    ▼
Trip strategy+plan (user Generate Plan): FishingStrategyService then TripPlanningService
    ▼
Spring Data JPA  (ddl-auto=validate)
    ▼
PostgreSQL + PostGIS (SRID 4326, Flyway)  +  S3 in prod
```

Packages under `com.aifishing`: `auth`, `user`, `fishingprofile`, `gear`, `boat`, `lake` (including `lake.ingestion` and `lake.processing`), `launch`, `trip`, `strategy`, `planning`, `fishingsession`, `feedback`, `common`.

`GET /trips/{id}` does not embed plans, waypoints, or sessions.

Geo conversion is centralized in `GeoMapper`: API `{lat, lng}` ↔ JTS/PostGIS `POINT(lng lat)` SRID 4326. Ontario ingest adds `CrsTransformer` (query `outSR=4326`, fallback NAD83→WGS84).

Ontario sources and ingest status: [docs/ontario-ingestion.md](docs/ontario-ingestion.md). Structure extraction, READY/PARTIAL/FAILED, and GeoJSON overlay: [docs/lake-processing.md](docs/lake-processing.md). GIS vs Vision vs Hybrid benchmark: [docs/structure-benchmark.md](docs/structure-benchmark.md). Phase 4 fishing context and AI strategy: [docs/fishing-strategy.md](docs/fishing-strategy.md). Phase 5 candidates, ranking, and routes: [docs/trip-planning.md](docs/trip-planning.md). Phase 8.8 time-aware scheduling: [docs/time-aware-planning.md](docs/time-aware-planning.md). Phase 8.7 boat launch selection: [docs/boat-launch-selection.md](docs/boat-launch-selection.md). Phase 8.6 boat equipment vs resolved capability: [docs/boat-capability.md](docs/boat-capability.md). Phase 6 guided session, GPS, and pause accounting: [docs/guided-fishing-session.md](docs/guided-fishing-session.md). Phase 7 catch, effort, CPUE, empirical ranking: [docs/fishing-feedback.md](docs/fishing-feedback.md). **Map rendering (Mapbox, not GIS):** [`../AI-Fishing-FE/docs/map-platform.md`](../AI-Fishing-FE/docs/map-platform.md). **AWS production:** [docs/production-deployment.md](docs/production-deployment.md) and [`infra/`](infra/).

## Local startup

Requires Java 21+ and Docker.

```bash
cd AI-Fishing-BE
docker compose up -d
export JAVA_HOME=/path/to/jdk-21-or-newer
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Dev user header: `X-User-Id: 11111111-1111-1111-1111-111111111111` (omitting it uses this seed user)

```bash
mvn test
```

## Database tables

Flyway is the schema source of truth (`spring.jpa.hibernate.ddl-auto=validate`).

- `users`
- `fishing_profiles` (JSONB `preferred_species`, `preferred_fishing_styles`)
- `gear`
- `boats` (JSONB `propulsion_types`, `motors`; `primary_transit_propulsion_type`; keep `max_speed_kmh`)
- `boat_capability_profiles` (shared sanitized cache; per-metric source/confidence)
- `lakes` (`centroid`, `boundary`, `time_zone_id`, `ogf_id`, bbox, identity metadata, `processing_status`)
- `trips` (`fishing_mode`, `primary_target_species`, JSONB `secondary_target_species`)
- `trip_plans`, `trip_waypoints`, `trip_planning_runs` (Phase 5; plans bind to a recorded strategy snapshot)
- `trip_launch_selections` (Phase 8.7; 1:1 with trips, not canonical access)
- `fishing_sessions` (`plan_version`, `paused_at`, `total_paused_seconds`, JSONB `summary`; one unfinished `ACTIVE`/`PAUSED` per user)
- `session_location_points`, `session_waypoint_progress`, `session_client_events`, `session_pause_intervals`
- `catch_events`, `catch_photos`, `fishing_effort_segments` (Phase 7–8; effort is derived on session end; photos are optional)
- `lake_dataset_status`, `raw_data_objects`
- Ontario canonical: `lake_boundaries`, `lake_waterways`, `wetlands`, `bathymetry_contours`, `bathymetry_points`, `lake_fish_species`, `fish_stocking_records`, `fish_habitats`, `lake_access_points`, `fishing_restrictions`
- Structure analysis: `lake_analysis_runs`, `lake_feature_status`, `lake_features`, `derived_analysis_artifacts`
- `trip_strategy_runs` (Phase 4 jsonb context / weather / profile; `feature_analysis_version`)

## APIs (`/api/v1`)

| Method | Path | Notes |
| --- | --- | --- |
| GET, PATCH | `/me` | Current user |
| GET, PUT | `/me/fishing-profile` | Upsert |
| GET, POST | `/me/gear` | |
| GET, PATCH, DELETE | `/me/gear/{id}` | DELETE deactivates; other users get 404 |
| GET, POST | `/me/boats` | Equipment + optional `resolvedCapability` preview on create |
| GET, PATCH, DELETE | `/me/boats/{id}` | Preview is equipment baseline only (not weather-adjusted) |
| POST | `/me/boats/{id}/resolve-capability` | Explicit refresh of non-overridden metrics |
| GET | `/lakes?query=` | Shared catalog, read-only |
| GET | `/lakes/{id}` | No ingestion or processing details |
| POST | `/admin/lakes/{id}/import` | Dev/admin; requires `app.admin.enabled=true` |
| GET | `/admin/lakes/{id}/datasets` | Dataset status |
| GET | `/admin/lakes/{id}/data-summary` | Coverage + analysis quality |
| GET | `/admin/lakes/data-summary?ids=` | Multi-lake comparison |
| POST | `/admin/lakes/{id}/process?pipeline=` | GIS (default), VISION, or HYBRID |
| POST | `/admin/lakes/{id}/benchmark` | Run A then B then C; merge screenshot metrics file |
| GET | `/admin/lakes/{id}/benchmark` | Last comparison report |
| GET | `/admin/lakes/benchmark-summary?ids=` | Four-lake benchmark fields |
| GET | `/admin/lakes/{id}/features` | Per-type status; `pipeline` defaults to GIS |
| GET | `/admin/lakes/{id}/features.geojson` | Overlay; `pipeline`, `type`, `minConfidence`, `analysisVersion` |
| GET | `/admin/lakes/{id}/map.png` | Latest canonical bathymetric render |
| GET | `/lakes/{id}/boat-launches` | `boat_launch=true` only; `routable` is water-anchor success |
| POST | `/lakes/{id}/boat-launches/custom-preview` | Snap preview; does not persist |
| GET, POST | `/trips` | Optional nested `launchSelection` (BOAT); SHORE clears it |
| GET, PATCH | `/trips/{id}` | Trip only; lake-local date/times; no strategy or plan embed |
| GET | `/lakes/{id}/planning-capabilities` | GIS and HYBRID readiness only; does not process |
| POST | `/trips/{id}/plan` | User Generate Plan: `featurePipeline` GIS (Standard) or HYBRID (AI-Enhanced Experimental). Omitted uses `app.planning.user-default-feature-pipeline`. |
| GET | `/trips/{id}/plan` | Latest GENERATED or ACCEPTED |
| GET | `/trips/{id}/plans` | Version history |
| GET | `/trips/{id}/plan/map-data` | Compact waypoint points only |
| POST | `/trips/{id}/fishing-sessions` | Start from current GENERATED/ACCEPTED plan; one unfinished session per user |
| GET | `/fishing-sessions/{id}` | Session + waypoint progress |
| POST | `/fishing-sessions/{id}/locations` | GPS batch (`clientPointId` idempotent) |
| GET | `/fishing-sessions/{id}/navigation` | Reconcile current waypoint |
| GET | `/fishing-sessions/{id}/track` | Stored track |
| POST | `/fishing-sessions/{id}/pause` `/resume` `/end` | `{ clientEventId, occurredAt }` |
| POST | `/fishing-sessions/{id}/waypoints/{id}/arrive` `/skip` `/complete` | Same body |
| POST, GET | `/fishing-sessions/{id}/catches` | Idempotent `clientCatchId`; delayed OK on COMPLETED |
| GET, PATCH | `/catches/{id}` | Ownership 404 |
| POST | `/catches/{id}/void` | Soft void |
| POST | `/catches/{id}/photos/upload` | Presigned PUT; server-owned key |
| POST | `/catches/{id}/photos/{photoId}/complete` | HEAD object; persist actual size/type |
| GET | `/catches/{id}/photos` | READY photos + short-lived GET URLs |
| DELETE | `/catches/{id}/photos/{photoId}` | Soft delete |
| GET | `/fishing-sessions/{id}/performance` | Effort, CPUE, smoothed `bestWaypoint` |
| GET | `/admin/fishing-sessions/{id}` | Point and event counts |
| GET | `/admin/fishing-sessions/{id}/performance` | Same payload without ownership filter |
| GET | `/admin/lakes/{id}/empirical-summary` | Aggregates only; no coordinates |
| POST | `/admin/trips/{id}/strategy` | Generate StrategyProfile; OpenAI + Open-Meteo mocked in `mvn test` |
| GET | `/admin/trips/{id}/strategy` | Latest COMPLETED |
| GET | `/admin/trips/{id}/strategy-runs` | Run history |
| GET | `/admin/trips/{id}/strategy/context` | FishingContext (summaries only) |
| GET | `/admin/trips/{id}/planning-runs` | Planning run history + filter_summary on the detail route |

Trip date/start/end are wall-clock times in the lake `timeZoneId` (Head Lake: `America/Toronto`). `BOAT` requires an owned active boat; `SHORE` forbids `boatId`.

## Seed (dev profile)

- User `dev@aifishing.local` (local `X-User-Id`; production uses Cognito JIT)
- Rod, reel, line, lure
- Fishing boat with `GAS_OUTBOARD` + `ELECTRIC_TROLLING`, primary transit gas, `maxSpeedKmh` 42 (not a measured cruise override)
- Head Lake, Rice Lake, Lake Scugog, Lake Simcoe (Ontario; Head Lake is the primary acceptance lake)
- One `BOAT` trip targeting smallmouth, secondary walleye

Real Ontario bootstrap (Phase 8.5): [`docs/production-data-bootstrap.md`](docs/production-data-bootstrap.md) and [`scripts/bootstrap-validation-lakes.sh`](scripts/bootstrap-validation-lakes.sh). Do not skip a live import merely because a dataset is `AVAILABLE`.

Admin import example (Head Lake):

```bash
curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/import
curl -X POST -H "X-User-Id: 11111111-1111-1111-1111-111111111111" \
  http://localhost:8080/api/v1/admin/lakes/44444444-4444-4444-4444-444444444444/process
```
