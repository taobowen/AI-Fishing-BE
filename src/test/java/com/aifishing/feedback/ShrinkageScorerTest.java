package com.aifishing.feedback;

import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.performance.ShrinkageScorer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ShrinkageScorerTest {

    private final FeedbackProperties.Performance cfg = new FeedbackProperties.Performance();

    @Test
    void belowMinEffortIsNeutralWithZeroConfidence() {
        ShrinkageScorer.Result result = ShrinkageScorer.score(1, 0.05, cfg);
        assertThat(result.historicalPerformance()).isEqualTo(0.5);
        assertThat(result.evidenceConfidence()).isEqualTo(0);
        assertThat(result.rawHistoricalScore()).isEqualTo(0.5);
    }

    @Test
    void noDataIsNeutral() {
        ShrinkageScorer.Result result = ShrinkageScorer.score(0, 0, cfg);
        assertThat(result.historicalPerformance()).isEqualTo(0.5);
        assertThat(result.evidenceConfidence()).isEqualTo(0);
    }

    @Test
    void evidencePullsTowardRawScore() {
        ShrinkageScorer.Result result = ShrinkageScorer.score(8, 4, cfg);
        assertThat(result.evidenceConfidence()).isCloseTo(4.0 / 8.0, within(1e-9));
        assertThat(result.historicalPerformance()).isCloseTo(
                0.5 + result.evidenceConfidence() * (result.rawHistoricalScore() - 0.5),
                within(1e-9)
        );
        assertThat(result.historicalPerformance()).isGreaterThan(0.5);
        assertThat(result.historicalPerformance()).isLessThan(result.rawHistoricalScore());
    }

    @Test
    void zeroEffortCpueIsNullAndZeroCatchWithEffortIsZero() {
        assertThat(ShrinkageScorer.landedCpue(3, 0)).isNull();
        assertThat(ShrinkageScorer.landedCpue(0, 2)).isEqualTo(0.0);
        assertThat(ShrinkageScorer.landedCpue(4, 2)).isEqualTo(2.0);
    }

    @Test
    void blendUsesUserEffortShare() {
        ShrinkageScorer.Result personal = new ShrinkageScorer.Result(1, 0.8, 0.7, 0.71);
        ShrinkageScorer.Result global = new ShrinkageScorer.Result(0.5, 0.5, 0.2, 0.5);
        ShrinkageScorer.Result blended = ShrinkageScorer.blend(personal, global, 4, 4);
        assertThat(blended.historicalPerformance()).isCloseTo(0.605, within(1e-9));
    }
}
