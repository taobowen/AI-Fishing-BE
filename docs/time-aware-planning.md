# Time-aware trip scheduling (Phase 8.8)

Generate Plan still uses **one scheduler** for Standard GIS and AI-Enhanced HYBRID. This document is the contract for **when** spots are fished, not a second engine.

## Absolute time is the source of truth

Trip `plannedDate` + `fishingStartTime` / `fishingEndTime` are lake-local wall clocks. At generate they are converted **once** with `Lake.timeZoneId` to `tripStartAt` / `tripEndAt` (`ZonedDateTime` → `Instant`). Travel, dwell, WAIT, weather lookup, validation, and explanations all use those instants.

Persisted schedule (PostgreSQL `TIMESTAMPTZ`):

| Field | Where |
| --- | --- |
| `plannedArrivalAt` / `plannedDepartureAt` | `trip_waypoints` |
| `plannedLaunchDepartureAt` / `plannedReturnAt` | `trip_plans` |

`plannedArrivalTime` / `plannedDepartureTime` (`time`) are **derived lake-local copies** for old clients. They are not authoritative and must not be used across midnight, DST, or timezone changes.

GET returns ISO-8601 instants. GET does **not** recompute weather, scores, or times. A new forecast requires Generate Plan B (`SUPERSEDE` A).

## WAIT / IDLE

The beam may wait at the **launch or current stop** in slot-sized steps (`wait-options-minutes`, default 15 and 30) instead of traveling to a weak candidate just to advance the clock.

- `waitPenalty` is subtracted per wait, scaled by slot minutes.
- `maxTotalWaitMinutes` caps total idle time on a schedule.
- WAIT is persisted as `schedule_events` (`type=WAIT`) and shown on the plan timeline **only when the selected schedule contains it**.

## Azimuth convention

Every comparison uses the same convention (helper `AzimuthConvention`):

**0° = true north, 90° = east, 180° = south, 270° = west, clockwise, `[0, 360)`.**

Circular difference: `359°` vs `1°` is **2°**.

| Quantity | Meaning |
| --- | --- |
| `sunAzimuth` | Direction **toward** the sun (NREL SPA, mapped into this convention) |
| `shorelineWaterFacingAspect` | Horizontal unit vector **toward water** (outward normal). Side is chosen with `LakePlanningGeometry` `covers`, not polygon winding. |
| `slopeAspect` | Downslope / water-deepening azimuth when it can be verified |
| `LakeFeature.orientation` (raw) | **Along-feature tangent** from POINT chords / DROP_OFF contour lines (`atan2(east, north)`). **Not** water-facing and **not** downslope. Convert only after verifying the water / deeper side; otherwise confidence is `UNKNOWN`. Never feed the raw value into solar/wind scores. |
| `windFromAzimuth` | Meteorological: direction the wind **comes from** (Open-Meteo `wind_direction_10m`) |
| `windFlowAzimuth` | `windFromAzimuth + 180°` (mod 360) — direction air **moves toward**; this is compared to shore/slope aspect |

## Weather snapshot → time index

Planning deserializes `StrategyRun.weatherSnapshot` once. It does **not** call Open-Meteo per candidate.

`TimeIndexedWeather.at(Instant)` interpolates hourly wind, cloud, precip, air temp, and radiation. Wind direction uses circular interpolation.

Open-Meteo hourly now also stores `shortwave_radiation` and `direct_radiation` (W/m²). Old snapshots without radiation fall back to cloud + sun elevation, then elevation-only (`LOW` confidence).

`SolarInfluenceStrength`: usable radiation (gated by sun up) → else cloud × elevation → else elevation-only. Heavy overcast → ~0. Night → 0.

Water temperature remains unavailable. Air temperature may apply a small bounded `AIR_PROXY` effect (`app.planning.environment.temperature.air-proxy-weight`).

## Light preference (per strategy window)

Optional `StrategyTimeWindow.lightPreference`: `SUN_EXPOSED` / `SHADE_PREFERRED` / `TRANSITION_PREFERRED` / `NEUTRAL`. Missing / old snapshots → `NEUTRAL`.

GIS computes **objective** exposure only (`orientationExposure` × solar influence × confidence). That window’s preference **signs** the fishing effect. This is a shade *tendency* proxy, never a true physical shadow.

## Fishing wind vs boat weather

- `FishingWindEffect` classifies WINDWARD / LEEWARD / CROSSWIND from `windFromAzimuth` vs water-facing aspect. Same fishing environment for every boat type.
- `BoatWeatherPenalty` is feasibility against `wind-hard-reject-kmh` / `wind-penalty-kmh`. Fishing bonus cannot override a hard reject.

**Travel-leg feasibility:** every travel interval, including **return-to-launch**, is checked on the same snapshot (departure, midpoint, arrival, or interval maximum wind). A calm destination does not license a hard-wind transit. This is weather-time feasibility, not certified marine routing.

## Scheduler

Beam search over unused candidates × dwell options × optional WAIT, evaluating utility at the **estimated arrival Instant**. Default `beam-width` 8. Dwell options `[20, 30, 45, 60, 75, 90]` clamped by structure type, confidence, and remaining time. Diminishing returns: fishing value is the sum of slot utilities × `dwell-decay`.

Complexity on the order of `O(W × B × N × D × S)` plus wait branches, with `W≤5`, `B=8`, `N≤24`, `D≤6`.

Static intrinsic order can reverse when a later window is better (B in the morning, A in the afternoon). Prefer WAIT into a better window over a low-value filler spot.

Return-to-launch remains a **constraint** plus persisted `plannedReturnAt`. Totals include the return leg. `scheduleReserveMinutes` is slack before `tripEndAt` after the configured return buffer.

## Why this time?

Deterministic from stored score components and nearby-slot comparisons. Comparative sentences (“Better after 13:30”, “Conditions improve later”, “This spot is better earlier”) are emitted **only** when the scheduler compared the **same candidate** across nearby slots and the utility delta is ≥ `why-this-time-min-delta`. Otherwise non-comparative copy such as “Favorable light conditions during this window.” No LLM.

## Scores

- **Intrinsic** ranking: structure, depth, confidence, strategy/time-window match, gear, travel-access, historical. Trip-average `weatherCompatibility` is **not** part of this vector. `LaunchRecommender` still uses intrinsic quality with travel held at 0.5.
- **Time-adjusted utility** adds per-Instant strategy-window effect, solar, fishing wind, temperature, boat-weather penalty, and wait penalty, with `app.planning.environment.*` caps so solar/wind cannot promote a poor structure by themselves.

Waypoint `score_breakdown` stores both the intrinsic components and the time-condition fields. `environment` jsonb stores normalized azimuths and sources.

## Guided session

Guided fishing compares GPS to planned points and may write `arrivedAt` on **session** progress. It does not PATCH the plan or re-rank.

## Config

See `app.planning.schedule`, `app.planning.environment`, and `app.planning.spatial` in `application.yml`. Safety wind 40 / 25 stay in `app.planning.safety`. Algorithm version is `1.4.0`. `plannedDwellMinutes` is the full visit bracket (same as `plannedVisitMinutes`); fishing vs internal transit are separate fields.

Rice local-live Standard GIS Generate Plan (sequence, launch/return Instants, totals, weather snapshot): [reports/phase-8.8-rice-time-aware.md](reports/phase-8.8-rice-time-aware.md).

## Out of scope

Head / Scugog / Simcoe candidate failures, GIS/Vision/Hybrid extractors, access association, 80 m shoreline filter, live replan during a session, Phase 9.
