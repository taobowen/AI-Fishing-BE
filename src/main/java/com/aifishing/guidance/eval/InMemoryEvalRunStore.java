package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryEvalRunStore implements EvalRunStore {

    private final ConcurrentHashMap<UUID, EvalRun> runs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<EvalCaseResult>> results = new ConcurrentHashMap<>();

    @Override
    public void save(EvalRun run, List<EvalCaseResult> caseResults) {
        if (run == null || run.evalRunId() == null) {
            throw new IllegalArgumentException("eval run is required");
        }
        runs.put(run.evalRunId(), run);
        results.put(run.evalRunId(), new CopyOnWriteArrayList<>(
                caseResults == null ? List.of() : caseResults
        ));
    }

    @Override
    public Optional<EvalRun> find(UUID evalRunId) {
        return Optional.ofNullable(runs.get(evalRunId));
    }

    @Override
    public List<EvalCaseResult> findResults(UUID evalRunId) {
        List<EvalCaseResult> found = results.get(evalRunId);
        return found == null ? List.of() : List.copyOf(found);
    }

    public List<EvalRun> allRuns() {
        return new ArrayList<>(runs.values());
    }
}
