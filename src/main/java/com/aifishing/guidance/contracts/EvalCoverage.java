package com.aifishing.guidance.contracts;

/**
 * {@code evalCoverage} is {@code loadedCases / attemptedCases} when attempted &gt; 0.
 * SKIP counts in the denominator and is not a pass. An empty suite stays {@code null}, not 0.
 */
public record EvalCoverage(
        int loadedCases,
        int skippedCases,
        int attemptedCases,
        Integer passedCases,
        Double evalCoverage
) {
    public EvalCoverage {
        if (loadedCases < 0 || skippedCases < 0 || attemptedCases < 0) {
            throw new IllegalArgumentException("coverage counts must be >= 0");
        }
        if (passedCases != null && passedCases < 0) {
            throw new IllegalArgumentException("passedCases must be >= 0");
        }
        if (loadedCases > attemptedCases || skippedCases > attemptedCases) {
            throw new IllegalArgumentException("loaded/skipped cannot exceed attempted");
        }
        if (passedCases != null && passedCases > loadedCases) {
            throw new IllegalArgumentException("passedCases cannot exceed loadedCases");
        }
        evalCoverage = attemptedCases == 0 ? null : (double) loadedCases / (double) attemptedCases;
    }

    public static EvalCoverage of(int loadedCases, int skippedCases, int attemptedCases, Integer passedCases) {
        return new EvalCoverage(loadedCases, skippedCases, attemptedCases, passedCases, null);
    }

    public static EvalCoverage empty() {
        return of(0, 0, 0, null);
    }
}
