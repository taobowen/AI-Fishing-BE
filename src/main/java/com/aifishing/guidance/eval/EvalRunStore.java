package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists eval runs and case results only. Never writes session or learning tables.
 */
public interface EvalRunStore {

    void save(EvalRun run, List<EvalCaseResult> results);

    Optional<EvalRun> find(UUID evalRunId);

    List<EvalCaseResult> findResults(UUID evalRunId);
}
