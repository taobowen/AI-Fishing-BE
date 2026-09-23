# Agent versioning, frozen eval, and runtime control

Phase 1 names today’s production fishing Agent as catalog version **v1**, lists **available** versions in YAML, and selects the **active** production/candidate switch from a singleton DB row. Frozen replay can compare versions against the same recorded state. Runtime control can roll back, kill the Agent, disable live shadow, and defer learning mutations without a redeploy.

This is **not** a Session Replay UI, debug dashboard, feature-flag platform, traffic splitter, or a new Agent service.

## Version model and resolution seam

`AgentPolicyVersion` is a catalog entry: version id plus prompt / context / toolset / model / learning **profile ids**. It must drive component resolution, not only stamps.

`AgentPolicyResolver.resolve(String)` / `resolve(AgentPolicyVersion)` maps those profile ids to a `ResolvedAgentPolicy` (prompt instructions, context builder, tool registry, model client, learning profile). Runtime, eval, and shadow **bind once** at run start and put that policy on `AgentRunSnapshot.policy`. `DefaultFishingAgentRuntime` reads the bound policy; it does not re-read `GuidanceProperties` version labels for a bound run. `updateMetadata` may set decision/plan ids and must not rewrite policy or component version fields.

Stamp `AgentRunMetadata` (and `EvalComponentVersions`) from the resolved policy:

- `agentPolicyVersion`
- existing prompt / tool / context / model columns
- `learningAlgorithmVersion`
- `learningSnapshotVersion` (nullable)

v1 and v2 may resolve to **identical** profiles today. Future versions differ by pointing YAML at different profile ids, not by `if (version.equals("v3"))` in runtime.

## Registry (available) vs control (active)

| Seam | Owner | Meaning |
| --- | --- | --- |
| Available versions | `AgentPolicyRegistry` + YAML `app.guidance.policy.versions` | Catalog. `require(id)`, `all()`, `defaultVersion()`. Unknown ids fail. |
| Active switch | `AgentRuntimeControl` singleton (`agent_runtime_control`) | Live `productionVersion`, optional `candidateVersion`, `agentEnabled`, `shadowEnabled`, `learningEnabled`. |

YAML is not the live production/candidate switch. `DefaultFishingAgentFacade.bindProductionPolicy()` resolves `AgentRuntimeControlStore.load().productionVersion()`. Changing control does **not** rewrite in-flight or historical `agent_runs`; only **new** runs bind the new production version.

Boot: `AgentRuntimeControlBootValidator` `require`s production (and candidate if set). Unregistered DB versions fail startup. PATCH `/api/v1/admin/guidance/runtime-control` also `require`s version ids.

Rollback: PATCH `productionVersion` `v2` → `v1`. New runs resolve v1. Old runs keep their stamped version. No redeploy.

## `learningAlgorithmVersion` vs `learningSnapshotVersion`

- **`learningAlgorithmVersion`** — formula / bucketing version (`EmpiricalAlgorithm.VERSION`, stamped as `"1"`). This is not a snapshot of evidence.
- **`learningSnapshotVersion`** — nullable. Stays null until a true immutable learning-evidence snapshot exists. Do not call `EmpiricalAlgorithm.VERSION` a learning snapshot.

## Frozen replay

`VersionCompareRunner` / `EvalSuiteRunner.compare` always uses `FROZEN_REPLAY`. Same `FrozenAgentRunSnapshot` × `[v1, v2]` via `resolver.resolve`. `DefaultEvalRuntime.execute(snapshot, FROZEN_REPLAY, version)` overlays the eval tool registry on the resolved policy.

Uses **recorded** tool observations / empirical tool copies already on the snapshot (`DefaultEvalToolRegistry.recorded`). Does not hit live `historical_performance`. `EvalOnlyDecisionPersistence` never writes `guidance/current`, horizon, notifications, or learning.

`SIMULATION` stays skipped. There is no simulator in Phase 1.

### Non-reproducible dependencies

Frozen replay is as frozen as the recorded snapshot. It is **not** bit-for-bit reproduction of a live OpenAI run:

- **Live model non-determinism** — unless the model client is stubbed (deterministic fixture / scripted eval), the same prompt can yield a different `CandidateDecision`.
- **`historical_performance` is not a snapshot** — the live table can change after the original run. Frozen replay does not read it. `learningSnapshotVersion` stays null for that reason.
- **Tools never called on the original run are `UNKNOWN` on freeze** — `DefaultEvalToolRegistry` replays recorded observations; missing tools return `ToolResultStatus.UNKNOWN` rather than calling live APIs.

## Compare entry point, agreement vs outcome rates, UNSCORABLE

Entry point: `EvalSuiteRunner.compare(request)` / `compare(request, versions)` → `VersionCompareRunner.compare`.

- **Agreement rate** — comparable decision pairs (same vs different `primaryAction` + `targetTripWaypointId`) across versions / recorded action. Unscorable rows still participate in agreement when the versions agree with each other.
- **Outcome rates** — denominators are **comparable followed-action only** (replay action **and** target match the historically followed action/target). Unscorable rows are excluded from success and failure counts.
- **UNSCORABLE** — if the replayed version chooses a materially different action or target than the historically followed action/target, do **not** count historical `FISH_ON` (or other outcomes) as PASS or FAIL. Status `UNSCORABLE`, skip reason `ACTION_DIFFERENT_OUTCOME_UNSCORABLE`. This replaced the old shadow FAIL via `COUNTERFACTUAL_FISH_ON_IGNORED`.

Flyway: `V46__eval_unscorable_status.sql` (additive check constraint). `V45` is runtime control, not unscorable.

## Kill / rollback / shadow / learning

### Kill switch (`agentEnabled=false`)

- Do not invoke the model.
- Do not persist or deliver a fake Agent STAY as Agent advice (`finalizeSkipped`; no `agent_delivered_decisions` row).
- Session / GPS / waypoint / Fish Here / effort / catch continue with original-plan execution.
- Auditable skipped `agent_runs` row: `FALLBACK` + `fallback_reason=KILL_SWITCH`.
- No horizon write. No `ADVICE_CREATED`. No `GuidanceLearningHooks.onAdviceDelivered`.
- API body: last real `current()` if present; otherwise activity-derived continuation (`KillSwitchContinuation`) **without** persisting it as Agent advice.
- Live shadow is overridden off. Manual frozen evaluation remains allowed.

### Shadow

Live shadow only if `agentEnabled && shadowEnabled && candidateVersion != null` (`AgentRuntimeControl.liveShadowActive()`). Same frozen production state, eval-only persist, no `guidance/current`, no horizon, no notifications, no learning. `shadowEnabled=false` → no candidate eval run.

### Learning (`learningEnabled=false`)

Observation **stays on**:

- `agent_feedback`
- user-action observation (`UserActionObserver`)
- outcome attribution (`ATTRIBUTE_OUTCOME`)
- evaluation metrics (`ONLINE_METRICS_ROLLUP`)

Durable mutation is deferred (`LearningDispatchResult.DEFERRED`; claimer leaves mutation jobs pending):

- `AGGREGATE_EMPIRICAL`
- `PREFERENCE_UPDATE`
- `SESSION_SUMMARY` / `REFLECTION_EVAL`

Do not delete feedback or attribution rows. Do not drop enqueue of `ATTRIBUTE_OUTCOME`.

## Observability fields

Phase 2 will build Session Replay UI. Phase 1 only ensures a run is reconstructable by `runId` / `sessionId`. Reuse existing storage; do not duplicate:

| Need | Where |
| --- | --- |
| runId / decisionId / sessionId / trigger | `agent_runs` |
| state / context snapshots | `agent_runs.state_snapshot`, `context_snapshot` |
| tool calls + results + latency | `agent_tool_calls` |
| candidate / validation / delivered / fallback | child tables + `agent_runs.fallback_reason` |
| model / prompt / tool / context versions | existing columns |
| policy + learning algorithm + snapshot | V44 `agent_policy_version`, `learning_algorithm_version`, `learning_snapshot_version` |
| token usage / cost | V40 `usage_telemetry` / `raw_provider_usage` |
| wall latency | `started_at` / `finished_at` |

Historical rows with null `agent_policy_version` still load. Kill-switch skips record `fallback_reason` without a delivered-decision row.

## Intentionally deferred (Phase 2+)

- Session Replay UI, debug dashboard, timeline visualization
- Generic feature-flag platform / traffic splitting
- Simulator, opaque AI score, separate Agent service
- True immutable learning snapshots (`learningSnapshotVersion` stays null)
- New prompt text that changes live advice
- Mobile `agentPolicyVersion` on `GuidanceCurrentResponse`
