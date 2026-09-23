package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.EvalCoverage;

import java.util.Collection;
import java.util.Objects;

/**
 * Coverage helper for Workstream A. {@code evalCoverage = loaded / attempted}.
 * SKIP is attempted (denominator) and is never a pass. UNSCORABLE is loaded
 * and is never a pass or failure. An empty suite stays {@code null}, not {@code 0}.
 */
public final class EvalCoverageCalculator {

    private EvalCoverageCalculator() {
    }

    public static EvalCoverage fromResults(Collection<EvalCaseResult> results) {
        Objects.requireNonNull(results, "results");
        return fromStatuses(results.stream().map(EvalCaseResult::status).toList());
    }

    public static EvalCoverage fromStatuses(Collection<EvalCaseResultStatus> statuses) {
        Objects.requireNonNull(statuses, "statuses");
        int attempted = 0;
        int skipped = 0;
        int loaded = 0;
        int passed = 0;
        for (EvalCaseResultStatus status : statuses) {
            if (status == null) {
                throw new IllegalArgumentException("case status must not be null");
            }
            attempted++;
            if (status == EvalCaseResultStatus.SKIP) {
                skipped++;
                continue;
            }
            loaded++;
            if (status == EvalCaseResultStatus.PASS) {
                passed++;
            }
        }
        return EvalCoverage.of(loaded, skipped, attempted, passed);
    }

    public static boolean meetsMinCoverage(EvalCoverage coverage, double minCoverage) {
        Objects.requireNonNull(coverage, "coverage");
        if (minCoverage < 0 || minCoverage > 1) {
            throw new IllegalArgumentException("minCoverage must be in [0, 1]");
        }
        if (coverage.evalCoverage() == null) {
            return true;
        }
        return coverage.evalCoverage() + 1e-12 >= minCoverage;
    }

    public static void requireMinCoverage(EvalCoverage coverage, double minCoverage) {
        if (!meetsMinCoverage(coverage, minCoverage)) {
            throw new EvalCiAssertionException(
                    "evalCoverage " + coverage.evalCoverage()
                            + " is below minCoverage " + minCoverage
                            + " (loaded=" + coverage.loadedCases()
                            + " attempted=" + coverage.attemptedCases()
                            + " skipped=" + coverage.skippedCases()
                            + "; SKIP is not PASS)"
            );
        }
    }
}
