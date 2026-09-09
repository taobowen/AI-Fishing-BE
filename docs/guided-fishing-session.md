# Guided fishing session (Phase 6)

Phase 5 answers *where* to fish. Phase 6 executes that **exact** plan: start a session from the current `GENERATED` or `ACCEPTED` `TripPlan`, ingest GPS and manual waypoint actions, and record progress. The session snapshots `tripPlanId` + `plan_version` and **never** regenerates or mutates the plan.

This is **not** marine navigation. The plan-sequence line and live bearing are heuristics. FISH ON, catch logging, CPUE, and empirical ranking are Phase 7 ([fishing-feedback.md](fishing-feedback.md)).

## Unfinished session

`ACTIVE` and `PAUSED` are unfinished. One unfinished session per user (partial unique index plus a service 400). `COMPLETED` / `CANCELLED` cannot become `ACTIVE`.

```text
START → ACTIVE ⇄ PAUSED → COMPLETED
```

## Pause accounting

Pause intervals use the device `occurredAt` timestamp.

- Pause: `paused_at = occurredAt` (no-op if already `PAUSED`)
- Resume: `total_paused_seconds += elapsed(paused_at, occurredAt)`; `paused_at = null`
- End while paused: fold the open interval into `total_paused_seconds` first

End summary includes `totalPausedSeconds` and `activeFishingSeconds` (wall duration minus paused). Phase 7 also persists `session_pause_intervals` from the same timestamps so effort clipping is exact. See [fishing-feedback.md](fishing-feedback.md).

## Client events

Manual actions (`ARRIVE`, `SKIP`, `COMPLETE`, `END`, `PAUSE`, `RESUME`) send:

```json
{ "clientEventId": "...", "occurredAt": "..." }
```

`occurredAt` is when the user acted on the device. Duplicate `clientEventId` returns the current session and does not apply twice. A later `ARRIVE` with an earlier `occurredAt` does **not** resurrect a `SKIPPED` waypoint. `COMPLETED` / `CANCELLED` reject new events except idempotent replay of an already-applied id (and a no-op extra `END` on `COMPLETED`).

## GPS and arrival

`POST /api/v1/fishing-sessions/{id}/locations` accepts a batch (`clientPointId` unique per session). Every point is stored. Quality is `LOW_QUALITY` when accuracy exceeds `app.session.location.max-accuracy-m` or implied speed exceeds `impossible-speed-mps`. Arrival and dwell use **ACCEPTED** points only.

Auto waypoint machine (only while `ACTIVE`; paused GPS is stored but does not advance):

```text
seq 1 NAVIGATING, rest UPCOMING
NAVIGATING → ARRIVED → FISHING → COMPLETED → next NAVIGATING
```

Arrival requires **both**: elapsed ACCEPTED time inside `arrival-radius-m` ≥ `arrival-confirm-seconds` **and** ACCEPTED sample count in that trailing window ≥ `arrival-confirm-samples`. Distance is to the planned **entry portal** (or fishing corridor when present), not a shoreline LineString vertex. Two sparse points 30s apart with `confirm-samples=4` do not arrive. Auto-detection never overrides `SKIPPED`. Guided fishing does not replan.

Hysteresis: approach 150 m, arrive 60 m, depart 100 m, fishing dwell 120 s (`app.session.waypoint`).

## APIs (`/api/v1`)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/trips/{tripId}/fishing-sessions` | Optional `{ tripPlanId }`. Owned trip; plan GENERATED or ACCEPTED; ≥1 waypoint; no unfinished session. Does not generate a plan. |
| GET | `/fishing-sessions/{id}` | Ownership 404 |
| POST | `/fishing-sessions/{id}/locations` | GPS batch |
| GET | `/fishing-sessions/{id}/navigation` | Reconcile current waypoint + last accepted fix. Live distance/bearing on device is authoritative for UX. |
| GET | `/fishing-sessions/{id}/track` | Stored points |
| POST | `/fishing-sessions/{id}/pause` `/resume` `/end` | `{ clientEventId, occurredAt }` |
| POST | `/fishing-sessions/{id}/waypoints/{waypointId}/arrive` `/skip` `/complete` | Same body. Skip promotes the next `UPCOMING` waypoint. |
| GET | `/admin/fishing-sessions/{id}` | Counts of points and client events |

Fully offline **START** is out of scope. After an online start, the client may queue GPS and manual actions.

## Phase 7

Catch logging, effort, CPUE, and empirical ranking: [fishing-feedback.md](fishing-feedback.md). Phase 6 still does **not** compute CPUE itself; session end now triggers effort + performance recompute.
