# Session Replay

Internal admin reconstruction of what historically happened in a fishing session. Replay reads Phase 1 tables. It does **not** auto-rerun the LLM and does **not** generate chain-of-thought.

Owner of the admin contracts: `guidance/replay/`. Controllers live under `/api/v1/admin/**`, require `ROLE_ADMIN`, and are registered only when `app.admin.enabled=true`. There is no mobile API.

## Timeline sources

`SessionTimelineQuery` is `@Transactional(readOnly=true)`. Events are assembled by `TimelineAssembler` and ordered by **occurredAt → source priority → id**.

Source priority: `SESSION_EVENT`, `TRIGGER`, `AGENT_RUN`, `ADVICE`, `OBSERVED_ACTION`, `OUTCOME`, `HORIZON`, `CATCH`.

| Source | Kind | Table |
| --- | --- | --- |
| `SESSION_EVENT` | start/end/pause/resume, waypoints, Fish Here (`USER_STARTED_AD_HOC_FISHING`), lure, BITE, FISH_ON, CATCH_CREATED, PLAN_REPLANNED, GPS_ACCURACY_DEGRADED | `session_events` |
| `ADVICE` | `ADVICE_CREATED` / `ACCEPTED` / `REJECTED` / `ACKNOWLEDGED` | `session_events` |
| `TRIGGER` | `AGENT_TRIGGER` + dispatch status | `guidance_trigger_outbox` |
| `AGENT_RUN` | completed / failed / timeout / kill-switch. SHADOW labeled, never delivered | `agent_runs` |
| `OBSERVED_ACTION` | full `user_action_events` history | `user_action_events` |
| `OUTCOME` | full `outcome_attributions` history | `outcome_attributions` |
| `HORIZON` | `HORIZON_CHANGED` | `guidance_plan_versions` |
| `CATCH` | `LANDED` / `LOST` | `catch_events` |

`GPS_UPDATED` is excluded. Kill-switch (`FALLBACK` + `KILL_SWITCH`) and failed-before-decision runs stay visible as `AGENT_RUN` rows.

### Trigger ↔ run correlation

Timeline `AGENT_TRIGGER.runId` is set only when a **PRODUCTION** `agent_runs.trigger_outbox_id` matches the outbox row. Correlation is never by timestamp. An unexecuted trigger is never rendered as an Agent run (`runId` stays null).

Dispatch status: `PENDING`, `CLAIMED`, `DONE`, `FAILED`, plus `COALESCED` when `relatedTriggers` were merged into the row. There are **no `DROPPED` rows** — cooldown skips are not persisted on the outbox.

## Agent run trace fields

`GET /api/v1/admin/guidance/runs/{runId}` (`AgentRunTraceLoader`) reconstructs stored evidence only.

| Section | Contents |
| --- | --- |
| STATE | `agent_runs.state_snapshot` |
| CONTEXT | model-visible `context_snapshot` already stored |
| DECISION | candidate, validator, delivered or `fallbackReason`, committed horizon |
| OBSERVED ACTION | full `user_action_events` history **and** derived latest. `UNKNOWN` until a follow observation exists. `ACKNOWLEDGED` is not `FOLLOWED`. |
| OUTCOME | full `outcome_attributions` history **and** derived latest |

Also returned: `triggerOutboxId`, trigger + related + reason codes, versions (nullable OK, including `learningSnapshotVersion`), tools in observed order, latency, tokens/cost (`visibility` labeled, including SHADOW), visibility, status.

`deliveredLabeled` is false for SHADOW and kill-switch. Failed-before-decision has `decision: null`. Historical rows with null Phase 1 version stamps still load.

No generated or hidden chain-of-thought. Stored `shortExplanation` may appear; CoT keys are stripped in the WEB inspector.

## Admin APIs

All require `ROLE_ADMIN` and `app.admin.enabled=true`.

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/v1/admin/guidance/sessions/{sessionId}/timeline` | `SessionTimelineResponse` — summary, events, `originalPlan`, `latestShortHorizon`, `actualProgress` |
| GET | `/api/v1/admin/guidance/sessions/{sessionId}/map-trace` | `SessionMapTraceResponse` `{ originalPlan, gpsTrace, anchors }` |
| GET | `/api/v1/admin/guidance/runs/{runId}` | `AgentRunTraceResponse` |
| POST | `/api/v1/admin/guidance/runs/{runId}/evaluate` | `AgentRunEvaluateResponse` |

GETs do not mutate session or Agent state. Map-trace downsamples ordinary ACCEPTED GPS points and **never drops anchors**: waypoint arrival/departure, Fish Here ad-hoc start/end, BITE/FISH_ON locations, production delivered MOVE targets (SHADOW MOVE is not an anchor).

## Debug UI routes

`AI-Fishing-WEB`, trailing slash, `robots: noindex`, `SiteShell` skips `/admin*`.

| Route | Page |
| --- | --- |
| `/admin/guidance/` | session-id entry |
| `/admin/guidance/sessions/[sessionId]/` | summary, timeline (trigger status ≠ run), ReplayMap with anchors, inspector with observation/outcome **history**, Evaluate |

Prod auth is Cognito `ADMIN`. `DevAuthenticationFilter` `X-User-Id` admin grant is `@Profile({"dev","test"})` only. Prod does not honor that header.

## Original plan / dynamic horizon / actual behavior

These are three named collections, not one merged polyline:

| Collection | Meaning | Source |
| --- | --- | --- |
| `originalPlan` | trip waypoints as planned | `trip_waypoints` |
| `latestShortHorizon` | latest committed Agent short horizon | latest `guidance_plan_versions` + `guidance_plan_steps` |
| `actualProgress` | waypoint arrival/departure as fished | `session_waypoint_progress` |

A horizon rewrite does not replace the original plan. Fish Here is a `SESSION_EVENT` (`USER_STARTED_AD_HOC_FISHING`) plus ad-hoc map anchors; it is not an original-plan stop.

On the map: original plan GeoJSON, downsampled GPS, and preserved anchors. SHADOW is never drawn as a delivered MOVE.

## Evaluate reuses Frozen Replay / VersionCompare

`POST .../runs/{runId}/evaluate` → `AgentRunSnapshotLoader.load` → `EvalSuiteRunner.compare` with `ReplayMode.FROZEN_REPLAY` and suite kind `SHADOW_REPLAY`.

That is the same frozen compare path as Phase 1 versioning (`VersionCompareRunner`): same recorded snapshot × catalog versions. It writes **`guidance_eval_runs` only** (plus eval case results). No session mutation, no horizon write, no `guidance/current`, no learning enqueue, no invented rationale text.

Live OpenAI is not invoked when the eval runtime is deterministic; frozen replay still cannot invent CoT that was never stored. See `docs/guidance-agent-versioning.md` for agreement vs outcome rates and `UNSCORABLE`.

## Isolation audit (`agent_runs` SHADOW vs production readers)

Live candidate shadow persists on `agent_runs.visibility=SHADOW` so one run-detail loader works. Every production reader was audited:

| Reader | Isolation |
| --- | --- |
| `JpaDecisionPersistence.current` | `findByFishingSessionIdAndVisibilityOrderByStartedAtDesc(..., PRODUCTION)` then delivered child |
| `DefaultTriggerRouter.withinCooldown` | PRODUCTION runs only (SHADOW does not consume trigger cooldown) |
| Latest production-run | `findFirstByFishingSessionIdAndVisibilityOrderByStartedAtDesc(..., PRODUCTION)` |
| `AgentDeliveredDecisionRepository.findByFishingSessionIdOrderByCreatedAtAsc` | JPQL `visibility=PRODUCTION` — attribution (`OutcomeAttributor.loadDecisions`), map-trace delivered MOVE anchors |
| `GuidanceService.runBelongsToSession` | rejects SHADOW (no production feedback / learning on a shadow run) |
| Advice / attribution / learning / online metrics | keyed off `agent_delivered_decisions`. `ShadowAgentRunWriter` never writes delivered, horizon, `guidance/current`, notifications, or learning. SHADOW `trigger_outbox_id` stays null |
| Replay / eval snapshot loader / usage telemetry | **may** read SHADOW; tokens/cost labeled `visibility=SHADOW` |

Unfiltered `findFirstByFishingSessionIdOrderByStartedAtDesc` still exists for debug/replay. Production callers must use the visibility-filtered query.

SHADOW may be used for replay/debug, frozen evaluate, and separately labeled cost. SHADOW must never participate in delivered guidance, follow/outcome attribution, learning, production success metrics, or latest/current production-run queries.

## Remaining gaps

- **No chain-of-thought.** Replay shows stored state/context/tools/candidate/validation/decision only. Nothing is generated at read time.
- **`learningSnapshotVersion` stays null.** There is still no immutable learning-evidence snapshot. Frozen evaluate does not re-read live `historical_performance`.
- **No `DROPPED` trigger rows.** Cooldown/skip is not persisted on `guidance_trigger_outbox`. The timeline does not invent suppression events from timestamps.
- GPS downsample is geometric (anchors kept, ordinary points thinned); it is not a full GPS forensic export.
- Customer-facing replay, simulator, OTel, and traffic splitting are out of scope.
