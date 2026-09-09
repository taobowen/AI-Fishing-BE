# Fishing feedback (Phase 7)

Phase 6 records GPS and waypoint progress. Phase 7 records **what happened**: FISH ON catches, derived fishing effort, CPUE, and a shrinkage-smoothed `historicalPerformance` term used only when generating a **new** TripPlan. The plan currently being fished is never re-ranked.

Catch and effort stay structured PostGIS rows. This is not RAG and not a live AI coach.

## Catch lifecycle

`POST /api/v1/fishing-sessions/{sessionId}/catches` is idempotent on `(session, clientCatchId)`.

| Outcome | Meaning | Landed CPUE |
| --- | --- | --- |
| `PENDING` | FISH ON, details not finished | no |
| `LANDED` | Fish in hand / confirmed catch | yes |
| `LOST` | Hookup then lost | no (reported separately) |
| `UNKNOWN` | Never completed | no |

`VOIDED` stays in the table (accidental FISH ON). It is excluded from CPUE and ranking. Void is idempotent.

Delayed create/update/void is allowed on `COMPLETED` sessions when `occurredAt` is in `[startedAt, endedAt]`. `CANCELLED` rejects catches. A catch after `endedAt` is 400.

Ownership failures are **404**, same as sessions.

## Catch ↔ waypoint association

FISH ON may sync **before** pending GPS (Phase 6 flush is events then locations). The backend therefore does **not** require the waypoint to still be NAVIGATING/ARRIVED/FISHING.

1. Client `waypointId` is trusted as `CURRENT_WAYPOINT` only if it belongs to this session’s `TripPlan` **and** is temporally plausible for `occurredAt` (inside the session window; not already `SKIPPED`/`COMPLETED` before the catch). Client `lakeFeatureId` is never accepted — it is copied from that `TripWaypoint`.
2. Else nearest session waypoint within `app.feedback.catch.waypoint-association-radius-m` (150 m) → `NEAREST_WAYPOINT`.
3. Else `UNASSOCIATED`.

Missing/stale GPS: still create the catch; `location` is null. Do not invent coordinates. Do not log coordinates.

## Effort inference

`session_pause_intervals` are written from pause/resume/end using the same device `occurredAt` as Phase 6. Open intervals close on resume and on end-while-paused.

`FishingEffortService.recompute` runs **only on session end**. Catch create/update/void recomputes **performance only** — GPS did not change.

ACCEPTED GPS pairs whose `recorded_at` gap is ≤ `app.feedback.effort.max-sample-gap-seconds` (120) may be FISHING or TRAVEL. A larger unobserved gap is **UNKNOWN**, not continuous fishing. Do not fill a 20–30 minute GPS hole. Exception: a waypoint with `arrivedAt`/`departedAt` and **no** GPS in that window can contribute FISHING from those timestamps (manual on-spot without a track).

| Type | Rule |
| --- | --- |
| FISHING | Waypoint ARRIVED/FISHING at the sample time and the point is inside `departure-radius-m` (100 m). |
| TRAVEL | Navigating or outside the fishing radius, with samples inside the gap limit. |
| UNKNOWN | Insufficient evidence, lone low-speed samples, or excessive GPS gaps. **Not** fishing effort. |

Pause time is omitted, not UNKNOWN.

**Trolling MVP:** if the waypoint `recommendedTechniques` contains `TROLLING`, speed up to `max-trolling-speed-mps` (4) inside the fishing radius stays FISHING. This is not a trolling classifier. Fast movement outside the radius is still TRAVEL.

`derivation_version` (`effort-v1`) is stored on each segment. Raw GPS and progress remain the source facts.

## CPUE and session results

`landed CPUE = landedCount / fishingEffortHours` using `ACTIVE` + `LANDED` only.

- Zero fishing effort → CPUE **null** (UI shows “—”). Never Inf.
- Zero landed + positive effort → CPUE **0** (negative evidence).

`GET /api/v1/fishing-sessions/{id}/performance` returns per-waypoint `rawLandedCpue` and a **smoothed** score (same shrinkage as ranking). **`bestWaypointId`** is the waypoint with fishing effort ≥ `min-effort-minutes` (15) and the highest smoothed score. Trivial one-fish spikes do not win. Raw CPUE is still displayed on each row.

Admin: `GET /api/v1/admin/fishing-sessions/{id}/performance` and `GET /api/v1/admin/lakes/{lakeId}/empirical-summary` (aggregates only; no coordinates).

## Empirical ranking (new plans only)

Weights in `app.planning.ranking` still sum to 1.0. `strategy-match` is 0.20; `historical-performance` is 0.10. `historicalEvidenceConfidence` is **not** a weight; it is stored on `score_breakdown` JSONB.

`TripPlanningService.rank` loads empirical scores in **one** batched PostGIS query (`EmpiricalRankingProvider`), then `SpotRankingService` looks up by `featureId`. No N+1.

Spatial join uses **current** candidate geometry + `spatial-buffer-m` (75), not historical `lakeFeatureId` (provenance only):

Phase 8.9 CPUE grain is `min(reliable catch resolution, reliable effort resolution)`. Micro-target historical CPUE is written only when **both** landed evidence and FISHING effort are attributed at `fishing_target_id`. Catch on a micro target with effort only at Zone updates **Zone** evidence (`zone_id` on catch and effort). The same catch/effort is never added at micro + target + Zone in one ranking pass. Shrinkage and negative-evidence rules stay.

- FISHING segments with `track_geometry`: count only the **length fraction** of the track that intersects the buffered candidate, times segment duration.
- Segments without usable geometry: representative-point `ST_DWithin` fallback.
- Landed catch GPS: `ST_DWithin` against the same buffer.

Shrinkage:

```text
posteriorRate = (landed + priorRate * priorHours) / (effortHours + priorHours)
rawHistoricalScore = clamp(0.5 + 0.5 * tanh((posteriorRate - priorRate) / priorRate))
```

If allocated effort < `min-effort-minutes` (including no data): `historicalPerformance = 0.5`, `historicalEvidenceConfidence = 0`.

Otherwise:

```text
evidenceConfidence = effortHours / (effortHours + priorHours)
historicalPerformance = 0.5 + evidenceConfidence * (rawHistoricalScore - 0.5)
```

Personal vs lake-global posteriors are then blended with `userBlend = userEffortHours / (userEffortHours + user-blend-prior-hours)`. Global stats never leak another user’s coordinates through user APIs.

Species: only sessions whose trip `primaryTargetSpecies` matches the plan’s primary. Unknown-species catches stay stored but do not drive this score.

Catch create **must not** call generate/re-rank.

## APIs (`/api/v1`)

| Method | Path |
| --- | --- |
| POST, GET | `/fishing-sessions/{id}/catches` |
| GET, PATCH | `/catches/{id}` |
| POST | `/catches/{id}/void` |
| GET | `/fishing-sessions/{id}/performance` |
| GET | `/admin/fishing-sessions/{id}/performance` |
| GET | `/admin/lakes/{id}/empirical-summary` |
| POST | `/catches/{id}/photos/upload` |
| POST | `/catches/{id}/photos/{id}/complete` |
| GET | `/catches/{id}/photos` |
| DELETE | `/catches/{id}/photos/{id}` |

Photo failure does not void the catch. Never log URLs or coordinates.

## Photos

Optional catch photos use `catch_photos` and the private bucket prefix `catch-photos/`. `complete()` HEADs the server-owned key and rejects size/type/prefix mismatches. `S3RawDataStorage` remains lake ingest, not the photo bean.

## Privacy

Do not log catch or GPS coordinates. Other users’ sessions and catches are 404. Admin empirical summary is aggregates only.

## Phase 8 notes

Account linking is future work. Production auth is Cognito; see [production-deployment.md](production-deployment.md).
