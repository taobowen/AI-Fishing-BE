package com.aifishing.guidance.empirical;

import java.util.Collection;

public interface HistoricalPerformanceStore {

    /**
     * Locks affected grains in stable order, SUMs raw contribution columns, then
     * recomputes derived rates and shrinkage. Never averages stored CPUE/score/confidence.
     */
    void rebuildGrains(Collection<EmpiricalGrain> grains);
}
