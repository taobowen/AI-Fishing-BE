# Phase 8.8 Rice Lake time-aware Generate Plan (local-live)

- generatedAt: `2026-09-04T03:53:33Z`
- environment: `local-live`
- apiBase: `http://127.0.0.1:8080`
- PostGIS: `127.0.0.1:5433/aifishing`
- Generate Plan never called `process`.

This is a **Standard GIS** multi-hour BOAT plan. Head / Scugog / Simcoe Generate Plan success is **not** a Phase 8.8 requirement. AI-Enhanced HYBRID was attempted; it is **not** a scheduler failure.

## Trip

| | |
| --- | --- |
| Trip | `02121386-8330-47fa-8a32-b046b0416d64` |
| Lake | Rice `44444444-4444-4444-4444-444444444445` |
| Timezone | `America/Toronto` |
| Date / window | `2026-09-06` **07:00–16:00** (9 h) |
| Mode | `BOAT` / `FISHING_BOAT` `a08518f8-bf85-494d-95c4-4d99e135324a` |
| Species | `WALLEYE` |
| Launch mode | `AUTO_RECOMMENDED` |
| `tripStartAt` / `tripEndAt` | `2026-09-06T11:00:00Z` / `2026-09-06T20:00:00Z` |

## Structure snapshot

`GET /api/v1/lakes/{id}/planning-capabilities` at generate:

| Pipeline | Available | Status | analysisVersion |
| --- | --- | --- | --- |
| GIS | true | READY | `8c32813a-11b7-4a8f-8712-6586b697e5f0` |
| HYBRID | false | NOT_READY | — |

GIS feature counts on that analysis run: `HUMP` 120, `DROP_OFF` 47, `FLAT` 38, `BASIN` 33, `POINT` 2, `ISLAND_EDGE` 0 (240).

## Weather snapshot (one fetch)

Planning deserialized `StrategyRun.weatherSnapshot`. Open-Meteo was **not** called per candidate.

| | |
| --- | --- |
| Provider | `open-meteo` |
| `retrievedAt` | `2026-09-04T03:52:15.785862Z` |
| Availability | `FORECAST_AVAILABLE` |
| Hours stored | 10 (includes `shortwaveRadiation`; hour-0 shortwave `2.0` W/m²) |
| Trip-average cloud / wind | 4% / 15.7 km/h |
| Notes | Air temperature is forecast air, not observed lake water |

Clear-sky cloud with **near-zero morning radiation** produced `solarInfluenceStrength=0` on the first two stops (`solarInfluenceSource=RADIATION_DIRECT`). Strength rose through the day (0.16 → 0.46 → 0.68). Overcast collapse is covered by fixtures (`SolarInfluenceTest`), not this live hour.

## Launch and return

| | |
| --- | --- |
| Official access | `e00cb5a7-7225-4594-b3b8-ea0ecff828b6` (empty `SITE_NAME`, `UNKNOWN` ownership, `boat_launch=true`) |
| Verification | `AUTHORITATIVE` |
| Warnings | `OWNERSHIP_UNVERIFIED` (`accessConfidence` 0.55) |
| `routeStartPoint` | 44.26379, −78.06320 |
| `plannedLaunchDepartureAt` | `2026-09-06T11:00:00Z` (07:00 lake local) |
| `plannedReturnAt` | `2026-09-06T17:54:00Z` (13:54 lake local) |
| Deadline | `tripEndAt` 16:00 lake local (`20:00Z`). Return is **before** deadline. |
| `scheduleReserveMinutes` | 111 (= slack after return minus 15 min return buffer) |

## Totals

Algorithm `scheduleAlgorithmVersion` / `planningAlgorithmVersion`: **1.2.0**. Travel totals **include the return leg**.

| Field | Value |
| --- | ---: |
| Waypoints | 5 |
| `totalFishingMinutes` | 375 |
| `totalEstimatedTravelMinutes` | 38.99 (includes return) |
| `totalPlannedMinutes` | 414 |
| `totalWaitMinutes` | 0 |
| `scheduleEvents` | empty (no WAIT row to display) |
| `scheduleReserveMinutes` | 111 |

`GET /api/v1/trips/{id}/plan` returned the same Instant fields and utilities as Generate Plan (no live weather recompute).

## Sequence (lake-local `America/Toronto`)

| Seq | Type | Arrival | Departure | Dwell min | Solar strength | Why this time? |
| ---: | --- | --- | --- | ---: | ---: | --- |
| 1 | DROP_OFF | 07:02 | 08:17 | 75 | 0.00 | Favorable conditions during this window. |
| 2 | DROP_OFF | 08:26 | 09:41 | 75 | 0.00 | Favorable conditions during this window. |
| 3 | DROP_OFF | 09:43 | 10:58 | 75 | 0.16 | Favorable conditions during this window. |
| 4 | DROP_OFF | 11:05 | 12:20 | 75 | 0.46 | This spot is better earlier, around 09:50. |
| 5 | DROP_OFF | 12:24 | 13:39 | 75 | 0.68 | This spot is better earlier, around 10:24. |

ISO instants (authoritative): seq 1 `11:02:52Z`–`12:17:52Z` … seq 5 `16:24:21Z`–`17:39:21Z`. Derived `plannedArrivalTime` / `plannedDepartureTime` are lake-local copies only.

Comparative copy on seq 4–5 is from stored same-candidate slot comparison (not LLM). Seq 1–3 stayed non-comparative.

DROP_OFF facing is `UNKNOWN` here (both perpendiculars in water / raw tangent not used as water-facing). `rawOrientationUsedAsFacing` is false on persisted `environment`.

## HYBRID (not a scheduler failure)

`POST .../plan` with `"featurePipeline":"HYBRID"` returned `STALE_OR_MISSING_FEATURE_SNAPSHOT` / “HYBRID structure snapshot is stale relative to current lake data.” Capabilities already reported Hybrid **not** product-ready. Quota was not the blocker. Phase 8.8 does not require a Hybrid Rice plan.

## Warnings on the GIS plan

`REGULATION_COVERAGE_PARTIAL`, `Practical range estimation is uncertain without fuel capacity data.`, `BOAT_RANGE_LOW_CONFIDENCE`, `OWNERSHIP_UNVERIFIED`, `WINDOW_FALLBACK_GLOBAL`.

## First Generate Plan attempt (schema)

The first live call failed OpenAI structured output: `timeWindows.items` listed `lightPreference` in `properties` but not in `required`. OpenAI requires every property key in `required`; the field is now **required-but-nullable** (`type: ["string","null"]`). Stored/old snapshots may still omit it → `NEUTRAL`.
