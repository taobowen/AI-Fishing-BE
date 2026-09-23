package com.aifishing.guidance.learning;

import com.aifishing.guidance.contracts.LearningJobType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningMutationJobsTest {

    @Test
    void durableMutationExcludesObservationJobs() {
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.AGGREGATE_EMPIRICAL)).isTrue();
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.PREFERENCE_UPDATE)).isTrue();
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.SESSION_SUMMARY)).isTrue();
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.REFLECTION_EVAL)).isTrue();
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.ATTRIBUTE_OUTCOME)).isFalse();
        assertThat(LearningMutationJobs.isDurableMutation(LearningJobType.ONLINE_METRICS_ROLLUP)).isFalse();
        assertThat(LearningMutationJobs.isDurableMutation(null)).isFalse();
    }
}
