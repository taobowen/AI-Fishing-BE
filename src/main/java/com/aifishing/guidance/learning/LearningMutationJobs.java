package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;

import java.util.Set;

/**
 * Durable learning mutation job types. Observation jobs stay runnable when
 * {@code learningEnabled=false}.
 */
public final class LearningMutationJobs {

    private static final Set<LearningJobType> DURABLE_MUTATION = Set.of(
            LearningJobType.AGGREGATE_EMPIRICAL,
            LearningJobType.PREFERENCE_UPDATE,
            LearningJobType.SESSION_SUMMARY,
            LearningJobType.REFLECTION_EVAL
    );

    private LearningMutationJobs() {
    }

    public static boolean isDurableMutation(LearningJobType type) {
        return type != null && DURABLE_MUTATION.contains(type);
    }
}
