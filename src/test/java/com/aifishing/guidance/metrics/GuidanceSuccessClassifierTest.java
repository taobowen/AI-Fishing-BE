package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.OutcomeKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceSuccessClassifierTest {

    private static final UUID DECISION = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void followedFishOnFamilyIsSuccessAndLandingIsNotASuccessKind() {
        assertThat(GuidanceSuccessClassifier.classify(DECISION, true, OutcomeKind.FISH_ON))
                .isEqualTo(GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(GuidanceSuccessClassifier.classify(DECISION, true, OutcomeKind.CATCH_LANDED))
                .isEqualTo(GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(GuidanceSuccessClassifier.classify(DECISION, true, OutcomeKind.CATCH_LOST))
                .isEqualTo(GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(GuidanceSuccessClassifier.landing(OutcomeKind.CATCH_LANDED)).isTrue();
        assertThat(GuidanceSuccessClassifier.landing(OutcomeKind.FISH_ON)).isFalse();
        assertThat(GuidanceSuccessClassifier.landing(OutcomeKind.CATCH_LOST)).isFalse();
    }

    @Test
    void biteStaysOutOfNoFishAndAcceptedWithoutFollowIsNotSuccess() {
        assertThat(GuidanceSuccessClassifier.classify(DECISION, true, OutcomeKind.BITE))
                .isEqualTo(GuidanceSuccessKind.BITE_SIGNAL_ONLY)
                .isNotEqualTo(GuidanceSuccessKind.NO_FISH_SIGNAL)
                .isNotEqualTo(GuidanceSuccessKind.FISH_ON_SUCCESS);
        assertThat(GuidanceSuccessClassifier.classify(DECISION, true, OutcomeKind.NO_BITE))
                .isEqualTo(GuidanceSuccessKind.NO_FISH_SIGNAL);
        assertThat(GuidanceSuccessClassifier.classify(DECISION, false, OutcomeKind.FISH_ON))
                .isEqualTo(GuidanceSuccessKind.NOT_FOLLOWED);
        assertThat(GuidanceSuccessClassifier.classify(null, true, OutcomeKind.FISH_ON))
                .isEqualTo(GuidanceSuccessKind.UNATTRIBUTED);
    }
}
