package com.aifishing.guidance.empirical;

import com.aifishing.feedback.effort.service.FishingEffortService;
import com.aifishing.feedback.performance.EmpiricalPerformanceService;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.learning.GuidanceLearningOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EmpiricalAggregationService {

    private final SessionEmpiricalContributionCalculator calculator;
    private final HistoricalContributionStore contributionStore;
    private final HistoricalPerformanceStore performanceStore;
    private final FishingEffortService fishingEffortService;
    private final EmpiricalPerformanceService empiricalPerformanceService;
    private final GuidanceLearningOutboxService learningOutboxService;
    private final Clock clock;

    public EmpiricalAggregationService(
            SessionEmpiricalContributionCalculator calculator,
            HistoricalContributionStore contributionStore,
            HistoricalPerformanceStore performanceStore,
            FishingEffortService fishingEffortService,
            EmpiricalPerformanceService empiricalPerformanceService,
            GuidanceLearningOutboxService learningOutboxService,
            Clock clock
    ) {
        this.calculator = calculator;
        this.contributionStore = contributionStore;
        this.performanceStore = performanceStore;
        this.fishingEffortService = fishingEffortService;
        this.empiricalPerformanceService = empiricalPerformanceService;
        this.learningOutboxService = learningOutboxService;
        this.clock = clock;
    }

    public void enqueueAfterSessionRecompute(UUID fishingSessionId) {
        if (fishingSessionId == null) {
            return;
        }
        learningOutboxService.enqueue(
                fishingSessionId,
                LearningJobType.AGGREGATE_EMPIRICAL,
                "AGGREGATE_EMPIRICAL:" + fishingSessionId + ":" + clock.instant() + ":" + UUID.randomUUID(),
                Map.of(
                        "fishingSessionId", fishingSessionId.toString(),
                        "empiricalAlgorithmVersion", EmpiricalAlgorithm.VERSION
                )
        );
    }

    @Transactional
    public void aggregateSession(UUID fishingSessionId) {
        fishingEffortService.recompute(fishingSessionId);
        empiricalPerformanceService.recompute(fishingSessionId);
        SessionEmpiricalContributionCalculator.Calculated calculated = calculator.calculate(fishingSessionId);
        Set<EmpiricalGrain> affected = contributionStore.replaceSession(
                fishingSessionId,
                calculated.contributions(),
                clock.instant(),
                calculated.sourceHash()
        );
        performanceStore.rebuildGrains(affected);
    }

    @Transactional
    public void rebuildGrains(Collection<EmpiricalGrain> grains) {
        performanceStore.rebuildGrains(grains);
    }
}
