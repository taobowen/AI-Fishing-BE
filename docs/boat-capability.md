# Boat capability (Phase 8.6)

Phase 8.6 splits **what the user owns** from **what planning is allowed to assume**.

This is **not** certified speed, endurance, seaworthiness, navigation, or boating safety. Values are inland-fishing planning heuristics. Do not treat them as manufacturer ratings, Coast Guard classes, or a substitute for local judgement.

## Layers

| Layer | Where it lives | Weather? | Shown on boat screens? |
| --- | --- | --- | --- |
| Equipment | `boats` row (hull, propulsion list, motors, optional free-text notes) | no | yes |
| Baseline `ResolvedBoatCapability` | per-metric cruise / practical range / wind-wave, with source + confidence | no | yes (`ResolvedBoatCapabilityPreview`) |
| Effective `EffectiveBoatCapability` | baseline × that plan’s persisted StrategyRun weather snapshot | yes | **no** |

Phase 5 never parses HP, thrust, or model strings. It consumes the resolved numbers only.

## Per-metric priority

Cruise, practical range, and wind/wave each resolve independently:

1. `USER_OVERRIDE` on that user’s `Boat` (`measuredCruiseSpeedKmh` for speed only)
2. Shared cache row for the same fingerprint **and** the same `resolverVersion` / `promptVersion`
3. AI + web search (`AI_WEB_RESOLVED`)
4. AI estimate without usable web evidence (`AI_MODEL_ESTIMATED`)
5. YAML conservative fallback (`CONSERVATIVE_FALLBACK`)

Example merge: speed 17 `USER_OVERRIDE`, range 25 `AI_WEB_RESOLVED`, wind `MEDIUM` `AI_WEB_RESOLVED`.

`maxSpeedKmh` is **not** a measured override. After 8.6 planning does not use it as cruise. If override, cache, and AI all miss, it may be used as a weak low-confidence hint (`WEAK_LEGACY_MAX_SPEED`) for that boat only. Seed `42` stays on `max_speed_kmh`.

## Range math (reserve once)

- `estimatedPracticalRangeKm` — system estimate (cache / AI / fallback). May be `null` with low confidence. Do not invent 20 km.
- `systemUsableRangeKm` = `estimatedPracticalRangeKm × (1 − range-reserve-fraction)` when the estimate is present **and** confidence ≥ `low-range-confidence-threshold` (0.50). Default reserve is 0.30.
- `comfortableRoundTripRangeKm` — user planning cap. **Never** multiplied by 0.70 again.
- `effectiveUsableRangeKm` = `min(systemUsable, comfortableCap)` when the user cap exists; else `systemUsable`.
- If the estimate is null/low-confidence: do not invent a system range; still honor the user cap if present; warning `BOAT_RANGE_LOW_CONFIDENCE`.

## Shared cache

Table `boat_capability_profiles` is keyed by SHA-256 `configuration_fingerprint` (no user id).

Fingerprint includes structured equipment (hull type, make/model/year, propulsion list, primary transit, per-motor make/model/HP/thrust) plus **sanitized equipment facts** extracted from free text (voltage, Ah, LiFePO4, portable fuel litres). Raw `configurationDescription` is never hashed or stored in `normalized_configuration`.

The shared cache never stores `USER_OVERRIDE`. Cache origins are `AI_WEB_RESOLVED`, `AI_MODEL_ESTIMATED`, or `CONSERVATIVE_FALLBACK`.

A row stays valid until:

- fingerprint changes (new cache row; do not mutate profile A into B)
- `resolverVersion` or `promptVersion` changes
- explicit `POST /api/v1/me/boats/{id}/resolve-capability`

`cache-days` may exist in YAML as documentation. It **does not** force a 90-day AI refresh.

## AI

Capability uses the shared OpenAI Responses helper with **its own** `app.boat-capability.web-search-enabled` (default true). Strategy keeps `app.openai.web-search-enabled: false`.

Missing OpenAI, invalid JSON, or failed sanity checks must not fail boat create or trip planning. Invalid AI answers get one retry with error text, then **that metric** falls back.

Create boat succeeds even when resolve uses fallback.

Generate Plan calls AI only for metrics that are not already covered by a version-matching cache plus user overrides.

## Phase 5

`TripPlanningService.generate` resolves **once** after loading `Boat`, before candidates. Shore trips skip the resolver.

- `TravelTimeEstimator` uses `effective.cruiseSpeedKmh` (shore still `defaultShoreKmh`).
- `BoatCapabilityFilter` uses effective usable range / time budget when range is enforced. Unknown range uses conservative `maxLegKm` from YAML / type table, not a fake total range. Access unknown still skips launch-distance checks.
- `RoutePlanner` rejects a stop when accumulated launch→stops→candidate→launch exceeds `effectiveUsableRange`, or remaining range cannot cover the return. `legTooLong` uses effective max-leg.
- `SafetyFilter` still hard-rejects BOAT at 40 km/h wind. Wind-wave class is not a certified hull category.
- `SpotRankingService` fishing scores are **unchanged**. Do not scale proximity from boat range. A 0.95 spot stays 0.95; a weak boat may simply not select it.

Snapshots on `PlanningRun.inputSnapshot` and `TripPlan.metadata` include fingerprint, resolver version, per-metric baseline, weather derate summary, and usable-range breakdown. Planning algorithm version is `1.4.0` (spatial snapshot + ZoneVisitScope); boat resolver version remains `boat-capability-v1.1`.

## Primary transit

`propulsionTypes` stays a list (dual-motor boats keep both motors). `primaryTransitPropulsionType` is the propulsion used for route/transit capability. A trolling motor on a gas boat is equipment, not the transit number, unless the user marks it primary.

## Future GPS calibration (hook only)

A later phase may offer to calibrate cruise from observed GPS speed **after explicit user approval**. There is no automatic GPS write-back in 8.6.

## Config

See `app.boat-capability` in `application.yml` (reserve, confidence threshold, sanity bounds, YAML fallback by propulsion / hull / HP / thrust bands).
