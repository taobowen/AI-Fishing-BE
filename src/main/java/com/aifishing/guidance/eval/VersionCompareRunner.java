package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalRun;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.eval.VersionCompareReport.ActionDifference;
import com.aifishing.guidance.eval.VersionCompareReport.OutcomeRates;
import com.aifishing.guidance.eval.VersionCompareReport.VersionReplay;
import com.aifishing.guidance.reliability.ValidationSafetyClassifier;
import com.aifishing.guidance.runtime.GuidanceFallback;
import com.aifishing.guidance.spi.AgentRunSnapshotLoader;
import com.aifishing.guidance.spi.EvalRuntime;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Frozen replay of the same snapshot across catalog versions. Uses
 * {@link ReplayMode#FROZEN_REPLAY}, {@link EvalOnlyDecisionPersistence} (via
 * {@link DefaultEvalRuntime}), and recorded tool observations. Does not run
 * the simulator; {@link EvalSuiteKind#SIMULATION} stays skipped.
 */
@Component
public class VersionCompareRunner {

    public static final List<String> DEFAULT_VERSIONS = List.of(AgentPolicyVersion.V1, AgentPolicyVersion.V2);
    public static final String CASE_VERSION_SEPARATOR = "#";
    public static final String RECORDED_LABEL = "recorded";

    private final EvalRuntime evalRuntime;
    private final AgentRunSnapshotLoader snapshotLoader;
    private final EvalRunStore store;
    private final Clock clock;

    public VersionCompareRunner(
            EvalRuntime evalRuntime,
            AgentRunSnapshotLoader snapshotLoader,
            EvalRunStore store,
            Clock clock
    ) {
        this.evalRuntime = evalRuntime;
        this.snapshotLoader = snapshotLoader;
        this.store = store;
        this.clock = clock;
    }

    public VersionCompareReport compare(EvalSuiteRequest request) {
        return compare(request, DEFAULT_VERSIONS);
    }

    public VersionCompareReport compare(EvalSuiteRequest request, List<String> versions) {
        Objects.requireNonNull(request, "request");
        List<String> compareVersions = versions == null || versions.isEmpty()
                ? DEFAULT_VERSIONS
                : List.copyOf(versions);
        Instant started = clock.instant();
        UUID evalRunId = request.evalRunId() == null ? UUID.randomUUID() : request.evalRunId();
        List<EvalCaseFixture> cases = request.cases() == null ? List.of() : request.cases();

        EvalRuntime runtime = evalRuntime;
        if (evalRuntime instanceof DefaultEvalRuntime defaultRuntime) {
            runtime = defaultRuntime.withRecomputed(List.of());
        }

        List<EvalCaseResult> persisted = new ArrayList<>();
        List<VersionReplay> replays = new ArrayList<>();
        int evaluated = 0;
        int skipped = 0;
        int unscorable = 0;

        for (EvalCaseFixture fixture : cases) {
            if (fixture.kind() == EvalSuiteKind.SIMULATION) {
                skipped++;
                persisted.add(EvalCaseScorer.skip(
                        evalRunId,
                        fixture,
                        fixture.skipReason() == null ? EvalCaseScorer.SIMULATION_DEFERRED : fixture.skipReason()
                ));
                continue;
            }
            FrozenAgentRunSnapshot snapshot = fixture.snapshot();
            if (snapshot == null && fixture.sourceRunId() != null && snapshotLoader != null) {
                snapshot = snapshotLoader.load(fixture.sourceRunId()).orElse(null);
            }
            if (snapshot == null) {
                skipped++;
                persisted.add(EvalCaseScorer.skip(evalRunId, fixture, EvalCaseScorer.SNAPSHOT_NOT_FOUND));
                continue;
            }
            evaluated++;
            for (String version : compareVersions) {
                try {
                    AgentRunResult run = runtime.execute(snapshot, ReplayMode.FROZEN_REPLAY, version);
                    EvalCaseResult scored = EvalCaseScorer.scoreVersionCompare(evalRunId, fixture, run);
                    if (scored.status() == EvalCaseResultStatus.UNSCORABLE) {
                        unscorable++;
                    }
                    persisted.add(withCaseId(scored, persistedCaseId(fixture.caseId(), version)));
                    replays.add(new VersionReplay(fixture.caseId(), version, scored, run));
                } catch (RuntimeException ex) {
                    EvalCaseResult error = EvalCaseScorer.error(evalRunId, fixture, truncate(ex.getMessage()));
                    persisted.add(withCaseId(error, persistedCaseId(fixture.caseId(), version)));
                    replays.add(new VersionReplay(fixture.caseId(), version, error, null));
                }
            }
        }

        List<ActionDifference> differences = actionDifferences(cases, replays, compareVersions);
        OutcomeRates outcomeRates = outcomeRates(replays);
        Double agreementRate = agreementRate(replays, compareVersions);
        ValidationCounters validation = validationCounters(replays);

        EvalRun evalRun = new EvalRun(
                GuidanceSchemaVersion.VALUE,
                evalRunId,
                request.suite() == null ? "version-compare" : request.suite(),
                request.kind() == null ? EvalSuiteKind.SHADOW_REPLAY : request.kind(),
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                request.componentVersions(),
                request.pricingVersion(),
                EvalCoverageCalculator.fromResults(persisted),
                request.scenarioId(),
                request.seed(),
                request.simulationVersion(),
                request.scenarioTags(),
                started,
                clock.instant()
        );
        if (store != null) {
            store.save(evalRun, persisted);
        }

        return new VersionCompareReport(
                evalRunId,
                compareVersions,
                ReplayMode.FROZEN_REPLAY,
                evaluated,
                skipped,
                unscorable,
                agreementRate,
                validation.passRate,
                validation.safetyBlocks,
                validation.invalidMoves,
                validation.moveOscillation,
                validation.opportunityInCooldown,
                differences,
                outcomeRates,
                replays,
                started,
                evalRun.finishedAt()
        );
    }

    public static String persistedCaseId(String caseId, String version) {
        return caseId + CASE_VERSION_SEPARATOR + version;
    }

    static boolean sameDecision(DeliveredDecision left, DeliveredDecision right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.equals(left.primaryAction(), right.primaryAction())
                && Objects.equals(left.targetTripWaypointId(), right.targetTripWaypointId());
    }

    private static List<ActionDifference> actionDifferences(
            List<EvalCaseFixture> cases,
            List<VersionReplay> replays,
            List<String> versions
    ) {
        Map<String, EvalCaseFixture> fixtures = new LinkedHashMap<>();
        for (EvalCaseFixture fixture : cases) {
            fixtures.put(fixture.caseId(), fixture);
        }
        Map<String, Map<String, VersionReplay>> byCase = index(replays);
        List<ActionDifference> differences = new ArrayList<>();
        for (Map.Entry<String, Map<String, VersionReplay>> entry : byCase.entrySet()) {
            String caseId = entry.getKey();
            EvalCaseFixture fixture = fixtures.get(caseId);
            Map<String, VersionReplay> byVersion = entry.getValue();
            for (String version : versions) {
                VersionReplay replay = byVersion.get(version);
                DeliveredDecision delivered = replay == null ? null : replay.delivered();
                if (fixture == null || delivered == null) {
                    continue;
                }
                if (EvalCaseScorer.materiallyDifferentFollowedAction(fixture, delivered)) {
                    differences.add(new ActionDifference(
                            caseId,
                            RECORDED_LABEL,
                            version,
                            fixture.recordedPrimaryAction(),
                            fixture.recordedTargetTripWaypointId(),
                            delivered.primaryAction(),
                            delivered.targetTripWaypointId()
                    ));
                }
            }
            for (int i = 0; i < versions.size(); i++) {
                for (int j = i + 1; j < versions.size(); j++) {
                    VersionReplay left = byVersion.get(versions.get(i));
                    VersionReplay right = byVersion.get(versions.get(j));
                    DeliveredDecision leftDecision = left == null ? null : left.delivered();
                    DeliveredDecision rightDecision = right == null ? null : right.delivered();
                    if (leftDecision == null || rightDecision == null || sameDecision(leftDecision, rightDecision)) {
                        continue;
                    }
                    differences.add(new ActionDifference(
                            caseId,
                            versions.get(i),
                            versions.get(j),
                            leftDecision.primaryAction(),
                            leftDecision.targetTripWaypointId(),
                            rightDecision.primaryAction(),
                            rightDecision.targetTripWaypointId()
                    ));
                }
            }
        }
        return differences;
    }

    private static Double agreementRate(List<VersionReplay> replays, List<String> versions) {
        if (versions.size() < 2) {
            return null;
        }
        Map<String, Map<String, VersionReplay>> byCase = index(replays);
        int pairs = 0;
        int agreeing = 0;
        for (Map<String, VersionReplay> byVersion : byCase.values()) {
            for (int i = 0; i < versions.size(); i++) {
                for (int j = i + 1; j < versions.size(); j++) {
                    VersionReplay left = byVersion.get(versions.get(i));
                    VersionReplay right = byVersion.get(versions.get(j));
                    DeliveredDecision leftDecision = left == null ? null : left.delivered();
                    DeliveredDecision rightDecision = right == null ? null : right.delivered();
                    if (leftDecision == null || rightDecision == null) {
                        continue;
                    }
                    pairs++;
                    if (sameDecision(leftDecision, rightDecision)) {
                        agreeing++;
                    }
                }
            }
        }
        return pairs == 0 ? null : (double) agreeing / (double) pairs;
    }

    private static OutcomeRates outcomeRates(List<VersionReplay> replays) {
        int comparable = 0;
        int success = 0;
        int failure = 0;
        for (VersionReplay replay : replays) {
            EvalCaseResult result = replay.result();
            if (result == null || result.status() == EvalCaseResultStatus.SKIP
                    || result.status() == EvalCaseResultStatus.ERROR
                    || result.status() == EvalCaseResultStatus.UNSCORABLE) {
                continue;
            }
            comparable++;
            OutcomeKind outcome = result.recordedOutcomeKind();
            if (outcome == OutcomeKind.FISH_ON) {
                success++;
            } else if (outcome != null) {
                failure++;
            }
        }
        Double successRate = comparable == 0 ? null : (double) success / (double) comparable;
        Double failureRate = comparable == 0 ? null : (double) failure / (double) comparable;
        return new OutcomeRates(comparable, success, failure, successRate, failureRate);
    }

    private static ValidationCounters validationCounters(List<VersionReplay> replays) {
        int evaluated = 0;
        int valid = 0;
        int safetyBlocks = 0;
        int invalidMoves = 0;
        int moveOscillation = 0;
        int opportunityInCooldown = 0;
        for (VersionReplay replay : replays) {
            AgentRunResult run = replay.run();
            if (run == null) {
                continue;
            }
            DeliveredDecision delivered = run.delivered();
            if (delivered != null && delivered.fallbackUsed()
                    && GuidanceFallback.SAFETY_BLOCK.equals(delivered.fallbackReason())) {
                safetyBlocks++;
            }
            DecisionValidationResult validation = run.validation();
            if (validation == null) {
                continue;
            }
            evaluated++;
            if (validation.valid()) {
                valid++;
            }
            if (isInvalidMove(run)) {
                invalidMoves++;
            }
            if (hasCheck(validation, DecisionValidationCheck.MOVE_OSCILLATION)) {
                moveOscillation++;
            }
            if (hasCheck(validation, DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN)) {
                opportunityInCooldown++;
            }
        }
        Double passRate = evaluated == 0 ? null : (double) valid / (double) evaluated;
        return new ValidationCounters(passRate, safetyBlocks, invalidMoves, moveOscillation, opportunityInCooldown);
    }

    private static boolean isInvalidMove(AgentRunResult run) {
        if (run.candidate() == null || run.candidate().primaryAction() != GuidanceAction.MOVE) {
            return false;
        }
        return ValidationSafetyClassifier.hasInvalidWaypoint(run.validation());
    }

    private static boolean hasCheck(DecisionValidationResult validation, DecisionValidationCheck check) {
        if (validation == null || validation.issues() == null) {
            return false;
        }
        for (ValidationIssue issue : validation.issues()) {
            if (issue != null && issue.check() == check) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Map<String, VersionReplay>> index(List<VersionReplay> replays) {
        Map<String, Map<String, VersionReplay>> byCase = new LinkedHashMap<>();
        for (VersionReplay replay : replays) {
            byCase.computeIfAbsent(replay.caseId(), key -> new LinkedHashMap<>())
                    .put(replay.policyVersion(), replay);
        }
        return byCase;
    }

    private static EvalCaseResult withCaseId(EvalCaseResult result, String caseId) {
        return new EvalCaseResult(
                result.schemaVersion(),
                result.evalRunId(),
                caseId,
                result.status(),
                result.allowedActions(),
                result.forbiddenActions(),
                result.invariants(),
                result.actualDecision(),
                result.actualPrimaryAction(),
                result.recordedPrimaryAction(),
                result.recordedOutcomeKind(),
                result.skipReason(),
                result.errorMessage(),
                result.scenarioId(),
                result.seed()
        );
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "eval case failed";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record ValidationCounters(
            Double passRate,
            int safetyBlocks,
            int invalidMoves,
            int moveOscillation,
            int opportunityInCooldown
    ) {
    }
}
