package com.aifishing.feedback.effort.service;

import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FishingEffortAdHocTest {

    private static final Instant START = Instant.parse("2026-09-17T10:20:00Z");
    private static final Instant END = Instant.parse("2026-09-17T11:05:00Z");

    @Test
    void coveringAdHocIsAuthoritativeDuringNavigatingWindow() {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setStartedAt(START);
        stop.setEndedAt(END);

        assertThat(FishingEffortService.coveringAdHoc(List.of(stop), START.plusSeconds(60))).isSameAs(stop);
        assertThat(FishingEffortService.coveringAdHoc(List.of(stop), START)).isSameAs(stop);
        assertThat(FishingEffortService.coveringAdHoc(List.of(stop), END)).isNull();
        assertThat(FishingEffortService.coveringAdHoc(List.of(stop), START.minusSeconds(1))).isNull();
    }

    @Test
    void openStopCoversUntilEnded() {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setStartedAt(START);

        assertThat(FishingEffortService.coveringAdHoc(List.of(stop), START.plusSeconds(45 * 60))).isSameAs(stop);
    }
}
