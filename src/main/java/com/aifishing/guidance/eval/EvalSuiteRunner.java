package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.EvalCoverage;
import com.aifishing.guidance.contracts.EvalRun;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.runtime.ModelTurnClient;
import com.aifishing.guidance.spi.AgentRunSnapshotLoader;
import com.aifishing.guidance.spi.EvalRuntime;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Runs an offline eval suite and persists only eval / audit rows.
 */
@Component
public class EvalSuiteRunner {

    private final EvalRuntime evalRuntime;
    private final AgentRunSnapshotLoader snapshotLoader;
    private final EvalRunStore store;
    private final ModelTurnClient modelTurnClient;
    private final Clock clock;

    public EvalSuiteRunner(
            EvalRuntime evalRuntime,
            AgentRunSnapshotLoader snapshotLoader,
            EvalRunStore store,
            ModelTurnClient modelTurnClient,
            Clock clock
    ) {
        this.evalRuntime = evalRuntime;
        this.snapshotLoader = snapshotLoader;
        this.store = store;
        this.modelTurnClient = modelTurnClient;
        this.clock = clock;
    }

    public EvalRun run(EvalSuiteRequest request) {
        Objects.requireNonNull(request, "request");
        Instant started = clock.instant();
        UUID evalRunId = request.evalRunId() == null ? UUID.randomUUID() : request.evalRunId();
        List<EvalCaseFixture> cases = request.cases() == null ? List.of() : request.cases();
        List<EvalCaseResult> results = new ArrayList<>();
        int loaded = 0;
        int skipped = 0;
        int passed = 0;

        EvalRuntime runtime = evalRuntime;
        if (evalRuntime instanceof DefaultEvalRuntime defaultRuntime) {
            runtime = defaultRuntime.withRecomputed(request.recomputedComponents());
        }

        for (EvalCaseFixture fixture : cases) {
            if (fixture.kind() == EvalSuiteKind.SIMULATION) {
                skipped++;
                results.add(EvalCaseScorer.skip(
                        evalRunId,
                        fixture,
                        fixture.skipReason() == null ? EvalCaseScorer.SIMULATION_DEFERRED : fixture.skipReason()
                ));
                continue;
            }
            FrozenAgentRunSnapshot snapshot = fixture.snapshot();
            if (snapshot == null && fixture.sourceRunId() != null) {
                snapshot = snapshotLoader.load(fixture.sourceRunId()).orElse(null);
            }
            if (snapshot == null) {
                skipped++;
                results.add(EvalCaseScorer.skip(evalRunId, fixture, EvalCaseScorer.SNAPSHOT_NOT_FOUND));
                continue;
            }
            loaded++;
            try {
                AgentRunResult run = runtime.execute(snapshot, request.replayMode());
                EvalCaseResult scored = EvalCaseScorer.score(evalRunId, fixture, run);
                if (scored.status() == EvalCaseResultStatus.SKIP) {
                    skipped++;
                    loaded--;
                } else if (scored.status() == EvalCaseResultStatus.PASS) {
                    passed++;
                }
                results.add(scored);
            } catch (RuntimeException ex) {
                results.add(EvalCaseScorer.error(evalRunId, fixture, truncate(ex.getMessage())));
            }
        }

        int attempted = cases.size();
        Double coverage = attempted == 0 ? null : (double) loaded / attempted;
        EvalRun evalRun = new EvalRun(
                GuidanceSchemaVersion.VALUE,
                evalRunId,
                request.suite(),
                request.kind(),
                request.replayMode(),
                request.recomputedComponents(),
                request.componentVersions() == null ? currentVersions() : request.componentVersions(),
                request.pricingVersion(),
                new EvalCoverage(loaded, skipped, attempted, passed, coverage),
                request.scenarioId(),
                request.seed(),
                request.simulationVersion(),
                request.scenarioTags(),
                started,
                clock.instant()
        );
        store.save(evalRun, results);
        return evalRun;
    }

    /**
     * Frozen {@code v1}/{@code v2} compare. Always {@link ReplayMode#FROZEN_REPLAY};
     * recorded tool observations stay on the snapshot. Task D should call this or
     * {@link VersionCompareRunner#compare(EvalSuiteRequest)}.
     */
    public VersionCompareReport compare(EvalSuiteRequest request) {
        return new VersionCompareRunner(evalRuntime, snapshotLoader, store, clock).compare(request);
    }

    public VersionCompareReport compare(EvalSuiteRequest request, List<String> versions) {
        return new VersionCompareRunner(evalRuntime, snapshotLoader, store, clock).compare(request, versions);
    }

    private EvalComponentVersions currentVersions() {
        return new EvalComponentVersions(
                null,
                modelTurnClient == null ? null : modelTurnClient.provider(),
                modelTurnClient == null ? null : modelTurnClient.modelName(),
                modelTurnClient == null ? null : modelTurnClient.modelVersion(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "eval case failed";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record EvalSuiteRequest(
            UUID evalRunId,
            String suite,
            EvalSuiteKind kind,
            ReplayMode replayMode,
            List<RecomputedComponent> recomputedComponents,
            EvalComponentVersions componentVersions,
            String pricingVersion,
            List<EvalCaseFixture> cases,
            String scenarioId,
            Long seed,
            String simulationVersion,
            List<String> scenarioTags
    ) {
        public EvalSuiteRequest {
            recomputedComponents = List.copyOf(recomputedComponents == null ? List.of() : recomputedComponents);
            cases = List.copyOf(cases == null ? List.of() : cases);
            scenarioTags = scenarioTags == null ? null : List.copyOf(scenarioTags);
        }
    }
}
