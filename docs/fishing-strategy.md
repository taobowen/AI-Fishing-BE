# Fishing context and AI strategy (Phase 4)

Phase 4 turns a trip into a **FishingContext** (summaries only), attaches Open-Meteo weather when the forecast horizon allows, and asks a configurable OpenAI model for a **StrategyProfile**. The LLM chooses structure **types**, depths, and techniques. It never selects GPS, `LakeFeature` IDs, or navigational directions. Phase 5 consumes `systemConfidence`, time-window weights, and the recorded `featurePipeline` + `featureAnalysisVersion` — see [trip-planning.md](trip-planning.md).

Production structure input for **admin** strategy remains `app.strategy.feature-pipeline` (default **GIS**). Normal user Generate Plan uses `app.planning.user-default-feature-pipeline` (GIS) unless the user selects AI-Enhanced (HYBRID). There are no Head / Rice / Scugog / Simcoe branches.

## Flow

```text
Trip + User + Lake summaries
        │
        ▼
FishingContextBuilder ── WeatherProvider (Open-Meteo)
        │
        ▼
FishingStrategyReasoner (OpenAI Responses API, Structured Outputs)
        │
        ▼
StrategyProfileValidator  (retry once)
        │
        ▼
SystemConfidenceCalculator + backend DataLimitation codes
        │
        ▼
trip_strategy_runs  (COMPLETED | FAILED)
```

Admin (gated by `app.admin.enabled=true`):

| Method | Path |
| --- | --- |
| POST | `/api/v1/admin/trips/{tripId}/strategy` |
| GET | `/api/v1/admin/trips/{tripId}/strategy` (latest COMPLETED) |
| GET | `/api/v1/admin/trips/{tripId}/strategy-runs` |
| GET | `/api/v1/admin/trips/{tripId}/strategy/context` |

`GET /api/v1/trips/{id}` stays trip-only.

## Transaction boundaries

Open-Meteo and OpenAI HTTP are **never** held inside a database transaction.

1. Short `REQUIRES_NEW` transaction: insert `RUNNING`.
2. Build context + weather + model call with **no** open session.
3. Short `REQUIRES_NEW` transaction: persist `COMPLETED` or `FAILED`.

A failed regeneration **inserts** a `FAILED` row and does not delete the last `COMPLETED` row. Each COMPLETED run records `feature_analysis_version` from the last analysis for that pipeline. Phase 5 stores `strategyRunId` on plans and refuses to invent a snapshot when that version is missing.

## FishingContext

JSON-serializable summaries only. Lake centroid is used privately for weather and is **not** sent as a fishing spot.

- **Trip:** planned date, fishing hours, target species, mode, boat id. Timezone from `Lake.timeZoneId`.
- **User (optional):** experience, preferences, active gear, selected boat. Empty gear still yields a strategy (generic techniques). There is no deterministic gear-compatibility engine.
- **Lake:** name, province, optional depths, dataset coverage (`AVAILABLE` / `PARTIAL` / `NOT_AVAILABLE` / …), known canonical species names, stocking years, habitat types, regulation **coverage** (status + structured restriction count/types). **No geometries. No restriction `rawText`.**
- **Structure:** latest **valid** analysis run for the requested pipeline (admin YAML or explicit Generate Plan pipeline) plus per-type `{available, count, avgConfidence}`. Product readiness is centralized in `StructurePipelineReadinessService` (`READY` / `NOT_PROCESSED` / `FAILED` / `EMPTY` / `STALE` / `PROVENANCE_INVALID`) and mapped to strategy `READY` / `PARTIAL` / `FAILED` / `NOT_READY`.
- **Weather:** see below.

`NOT_AVAILABLE` means **we lack data**, not “the lake has none of that in nature.” Do not invent official species presence if the canonical species is unknown. If the trip target is absent from lake records, attach `TARGET_SPECIES_UNCONFIRMED` and still generate.

**Regulations:** the model is not asked whether fishing is legal and must not generate sanctuary geometry. Legal filtering remains a deterministic Phase 5 job on Phase 2 structured rows.

## Structure pipeline readiness

- Active pipeline **FAILED** or **NOT_READY** (no successful analysis run for `app.strategy.feature-pipeline`) → strategy run **FAILED**. Keep the last successful strategy. Do not fabricate structure availability.
- Active pipeline **READY** / **PARTIAL** with **zero features** → **degraded COMPLETED** with `STRUCTURE_NONE_AFTER_ANALYSIS`. Phase 5 refuses structure-based planning (`STRUCTURE_NONE_AFTER_ANALYSIS`).
- Successful analysis with some features → normal COMPLETED.

## Weather (Open-Meteo)

`WeatherProvider` / `OpenMeteoWeatherProvider`. Query: lake centroid + IANA timezone + trip date and fishing hours. Horizon is `app.weather.forecast-horizon-days` (16).

| `availability` | Meaning |
| --- | --- |
| `FORECAST_AVAILABLE` | Hourly windows overlapping fishing hours were parsed |
| `OUT_OF_FORECAST_RANGE` | Trip date beyond horizon — **do not fabricate** |
| `UNAVAILABLE` | e.g. past date relative to the lake timezone |
| `FAILED` | HTTP/parse error — strategy may still succeed |

Hourly samples may include `shortwaveRadiation` / `directRadiation` (Open-Meteo W/m²) for Phase 8.8 time-indexed solar gating. Planning interpolates the persisted snapshot; it does not re-fetch.

`retrievedAt` is always set when a fetch is attempted. `waterTemperatureAvailable` is **false** for this MVP; `waterTemperatureC` stays null. Air temperature is **not** observed lake temperature. The prompt, schema, and validator warn if a rationale asserts a numeric water temp.

`mvn test` mocks `WeatherProvider` and never calls `api.open-meteo.com`.

## StrategyProfile schema

Same `{type, weight, rationale?}` arrays globally and in time windows. **Do not** use a `structureWeights` map.

- `targetSpecies[]`: `{species, priority}`
- `structurePreferences[]`: day-level priors / fallbacks
- `timeWindows[]`: `{from, to, preferredDepthM{min,max}, structurePreferences[], techniques[], lightPreference?}`
  - Optional `lightPreference`: `SUN_EXPOSED` / `SHADE_PREFERRED` / `TRANSITION_PREFERRED` / `NEUTRAL`. OpenAI structured output lists it as required-but-nullable; stored/old snapshots may omit it → `NEUTRAL`. GIS still computes objective exposure only. See [time-aware-planning.md](time-aware-planning.md).
- `generalTechniques[]`: day-level priors / fallbacks
- `weatherInterpretation`: model-authored; not Phase 5 confidence
- `modelConfidence` optional (0–1): LLM self-score; **not** consumed by Phase 5
- `systemConfidence` (0–1): **backend-computed after validation**; this is what Phase 5 reads
- `warnings[]`
- `dataLimitations[]`: `{code, message?}` — backend is source of truth

The model must omit `systemConfidence`. JSON Schema lives in `com.aifishing.strategy.ai.StrategyJsonSchema` (`additionalProperties: false`, strict enums).

### Weight precedence (Phase 5)

1. For a given fishing time, use that window’s `structurePreferences` and `techniques` as the **effective** weights.
2. Global `structurePreferences` / `generalTechniques` are **day-level priors**. Use them only when a window omits a type/technique, or as fallback if a window list is empty.
3. Window weights **take precedence** over globals for types/techniques they list. Do not average global + window unless Phase 5 later defines a merge. Phase 4 stores both and documents precedence only.

## systemConfidence

`SystemConfidenceCalculator` produces a score in `[0.05, 0.95]`. It does **not** pass through `modelConfidence`.

Starting from base `0.62`:

| Signal | Adjustment |
| --- | --- |
| Weather `FORECAST_AVAILABLE` | +0.12 |
| Weather `OUT_OF_FORECAST_RANGE` | −0.08 |
| Weather `UNAVAILABLE` / `FAILED` | −0.10 |
| Pipeline `READY` | +0.12 |
| Pipeline `PARTIAL` | +0.04 |
| Pipeline `FAILED` / `NOT_READY` | −0.16 |
| `STRUCTURE_NONE_AFTER_ANALYSIS` | −0.10 |
| `STRUCTURE_DATA_LOW_CONFIDENCE` | −0.08 |
| Mean feature confidence | `(mean − 0.5) × 0.10` |
| Target species confirmed | +0.06 |
| `TARGET_SPECIES_UNCONFIRMED` | −0.08 |
| Each distinct limitation code | −0.03, **capped** at −0.20 |

## Data limitation codes

Backend attaches these from context. The model may echo warnings but is not the source of truth.

`WEATHER_UNAVAILABLE`, `WEATHER_OUT_OF_FORECAST_RANGE`, `WEATHER_FAILED`, `WATER_TEMPERATURE_UNAVAILABLE`, `BATHYMETRY_PARTIAL`, `BATHYMETRY_UNAVAILABLE`, `VEGETATION_DATA_UNAVAILABLE`, `BOTTOM_SUBSTRATE_UNAVAILABLE`, `TARGET_SPECIES_UNCONFIRMED`, `STRUCTURE_DATA_LOW_CONFIDENCE`, `STRUCTURE_NONE_AFTER_ANALYSIS`, `STRUCTURE_PIPELINE_NOT_READY`, `REGULATION_COVERAGE_PARTIAL`, `REGULATION_DATA_UNAVAILABLE`, `FISH_HABITAT_PARTIAL`, `FISH_HABITAT_UNAVAILABLE`.

## Prompt rules

Versioned in `StrategyPromptFactory` (`app.openai.strategy-prompt-version`, default `fishing-strategy-v1`):

- Not choosing spots; no coordinates or feature IDs
- Do not invent structures; `NOT_AVAILABLE` ≠ absent in nature
- Do not make legal decisions; no regulation raw text
- Air temperature ≠ water temperature
- Prefer existing gear when known
- Time-window arrays are the in-window plan; globals are priors
- Concise rationales

Optional web search (`app.openai.web-search-enabled`, default false) must not override canonical geography or decide legality. Citations persist in `source_metadata`.

Missing API key → run `FAILED` immediately. `app.openai.model` remains the Vision model; `app.openai.strategy-model` is YAML-only for Phase 4.

## Validation

Reject (do not silently rewrite major errors): weights in `[0,1]`; depth `min <= max`; recommended max depth ≤ lake `maxDepthM` when known; time windows inside trip hours with no interior overlap; known `FeatureType` / `TechniqueType` / `FishSpecies`; structure/technique lists are arrays; no coordinate-like numbers or `featureId` in rationales; unavailable structure types must not get strong weights (≥ 0.6) without an explicit warning **and** a typed structure limitation.

Retry **once** with validator errors in the next user turn; if still invalid → `FAILED`, keep previous `COMPLETED`.

## Persistence

Flyway `V12__trip_strategy_runs.sql`: `id`, `trip_id`, `status`, `model_id`, `prompt_version`, `feature_pipeline`, timestamps, jsonb `fishing_context` / `weather_snapshot` / `strategy_profile` / `source_metadata` / `usage_metadata`, `error_message`.

## Config

```yaml
app:
  openai:
    model: gpt-4o                    # Phase 3.1 Vision
    strategy-model: gpt-4o           # Phase 4
    strategy-prompt-version: fishing-strategy-v1
    web-search-enabled: false
  strategy:
    feature-pipeline: GIS
  planning:
    user-default-feature-pipeline: GIS
  weather:
    provider: open-meteo
    forecast-horizon-days: 16
    timeout-seconds: 20
    base-url: https://api.open-meteo.com
```

## Synthetic Head example

Fixture Head Lake (`44444444-4444-4444-4444-444444444444`), trip 06:00–15:00 targeting smallmouth with secondary walleye, GIS pipeline READY with at least one HUMP summary:

- Context includes secondary species `WALLEYE`, weather `retrievedAt`, `waterTemperatureAvailable=false`, regulation coverage without `rawText`.
- Mocked reasoner returns window 06:00–10:00, depth 2–4 m, HUMP + Ned Rig.
- Persisted profile has backend `systemConfidence` independent of `modelConfidence`.

Live Ontario / OpenAI counts are **not invented here**. Run admin strategy against local PostGIS after Phase 2 import and a live model key for real numbers.

## Four-lake context (fixture vs live)

The same `FishingContextBuilder` is used for every lake. Fixture tests assert **context** differs (dataset coverage, pipeline readiness, per-type counts) — not lake-name `if`s.

| Lake | Fixture context (tests) | Live Ontario / OpenAI |
| --- | --- | --- |
| Head | GIS READY, HUMP present, bathy AVAILABLE | see [reports/four-lake-hybrid-planning.md](reports/four-lake-hybrid-planning.md) |
| Rice | READY, zero structure, bathy NOT_AVAILABLE | see four-lake report (live GIS 240, not the fixture) |
| Scugog | PARTIAL, BASIN present, bathy PARTIAL | see four-lake report (live GIS 7) |
| Simcoe | READY, DROP_OFF + FLAT | see four-lake report (live GIS 1067) |

## MVP limits

- Open-Meteo air forecast only; no lake water temperature
- No candidate spots, ranking, routes, or Planning JSON
- No public `/api/v1/trips/{id}/strategy`
- Optional web search off by default
- Gear is passed through, not scored by a rules engine

## Phase 5 handoff

Planning will:

1. Load the `COMPLETED` `strategyRunId`
2. Use **`systemConfidence`** (not `modelConfidence`)
3. For each time window, use **window `structurePreferences` / techniques as effective weights**, falling back to global arrays
4. Filter `lake_features` for the StrategyRun’s recorded `featurePipeline` + `featureAnalysisVersion` (not live YAML)
5. Apply depth filters
6. Apply **deterministic** regulation filtering from Phase 2 structured data (not LLM text)
7. If `STRUCTURE_NONE_AFTER_ANALYSIS`, may refuse structure-based planning

Phase 4 never selects GPS rows.
