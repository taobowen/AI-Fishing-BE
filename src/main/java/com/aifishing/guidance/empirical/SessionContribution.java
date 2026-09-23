package com.aifishing.guidance.empirical;

public record SessionContribution(
        EmpiricalGrain grain,
        EmpiricalRawCounts raw
) {
}
