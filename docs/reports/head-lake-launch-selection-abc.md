# Head Lake launch selection A/B/C

Phase 8.7 live comparison. Three otherwise identical Head Lake BOAT plans; only launch mode changes. Official ≠ open/safe/legal. Custom ≠ a public ramp. This is **not** marine routing.

**Status:** `FAIL` / blocked on live Generate Plan — Head Lake 8.5 local-live import had `ACCESS_POINT` **0 records** (`AVAILABLE`, pagination complete). That is **coverage** (`NO_KNOWN_BOAT_LAUNCH` if AUTO is generated), not `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`. OpenAI was also unset on that validation run, so no Strategy/TripPlan was stored.

Environment: local-live (from [`production-data-validation.md`](production-data-validation.md))
Date: 2026-09-03
Head `boat_launch=true` list count: 0 (inferred from ACCESS_POINT raw=0; confirm with `GET /api/v1/lakes/44444444-4444-4444-4444-444444444444/boat-launches`)
Routable known launches: 0
Coverage vs unroutable: **NO_KNOWN** (no rows), not unroutable

| | A AUTO | B other official | C custom shoreline |
| --- | --- | --- | --- |
| Mode | AUTO_RECOMMENDED | OFFICIAL_SELECTED | CUSTOM_SELECTED |
| Official id / name | | n/a until a second known launch exists | n/a |
| Requested / shore / routeStart persisted | n/a | n/a | |
| Snap distance m | n/a | n/a | |
| Plan status / errorMessage | | | |
| Waypoints | | | |
| Route origin ≠ GIS access point | | | |

Notes:

- If AUTO fails with `NO_KNOWN_BOAT_LAUNCH`, B cannot run until ingest produces `boat_launch=true` rows. C (custom) may still preview/snap on shoreline.
- If AUTO fails with `NO_ROUTABLE_KNOWN_BOAT_LAUNCH`, known rows exist but water-anchor failed — keep that code in the report.
- Do not write custom coordinates into Expo Router params or `lake_access_points`.
- Fill observed numbers after a real Generate Plan with OpenAI and, if needed, a later access-point ingest.
