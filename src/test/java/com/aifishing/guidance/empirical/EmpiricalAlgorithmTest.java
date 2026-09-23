package com.aifishing.guidance.empirical;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmpiricalAlgorithmTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    @Test
    void bucketsFollowVersionedBoundaries() {
        assertThat(EmpiricalAlgorithm.seasonBucket(Instant.parse("2026-03-15T12:00:00Z"), TORONTO))
                .isEqualTo(SeasonBucket.SPRING);
        assertThat(EmpiricalAlgorithm.seasonBucket(Instant.parse("2026-09-12T16:00:00Z"), TORONTO))
                .isEqualTo(SeasonBucket.FALL);
        assertThat(EmpiricalAlgorithm.timeBucket(Instant.parse("2026-09-12T12:30:00Z"), TORONTO))
                .isEqualTo(TimeBucket.MORNING);
        assertThat(EmpiricalAlgorithm.windBucket(9.9)).isEqualTo(WindBucket.CALM);
        assertThat(EmpiricalAlgorithm.windBucket(10.0)).isEqualTo(WindBucket.MODERATE);
        assertThat(EmpiricalAlgorithm.windBucket(25.0)).isEqualTo(WindBucket.STRONG);
        assertThat(EmpiricalAlgorithm.windDirection(CompassDirection.SW)).isEqualTo(WindDirectionBucket.SW);
        assertThat(EmpiricalAlgorithm.windDirection(List.of(CompassDirection.N, CompassDirection.W)))
                .isEqualTo(WindDirectionBucket.VARIABLE);
    }

    @Test
    void zeroEffortRatesAreNullAndFourTwoZeroIsDistinct() {
        GuidanceProperties.Empirical cfg = new GuidanceProperties.Empirical();
        EmpiricalRawCounts signals = new EmpiricalRawCounts(3600, 4, 2, 0, 1);
        EmpiricalRawCounts empty = new EmpiricalRawCounts(3600, 0, 0, 0, 1);
        EmpiricalRawCounts noEffort = new EmpiricalRawCounts(0, 4, 2, 0, 1);

        EmpiricalAlgorithm.Derived withFishOn = EmpiricalAlgorithm.derive(signals, cfg);
        EmpiricalAlgorithm.Derived noBite = EmpiricalAlgorithm.derive(empty, cfg);
        EmpiricalAlgorithm.Derived zeroHours = EmpiricalAlgorithm.derive(noEffort, cfg);

        assertThat(withFishOn.biteRate()).isEqualTo(4.0);
        assertThat(withFishOn.fishOnRate()).isEqualTo(2.0);
        assertThat(withFishOn.landingRate()).isEqualTo(0.0);
        assertThat(withFishOn.cpue()).isEqualTo(0.0);
        assertThat(noBite.biteRate()).isEqualTo(0.0);
        assertThat(noBite.fishOnRate()).isEqualTo(0.0);
        assertThat(withFishOn.smoothedScore()).isGreaterThan(noBite.smoothedScore());
        assertThat(zeroHours.biteRate()).isNull();
        assertThat(zeroHours.fishOnRate()).isNull();
        assertThat(zeroHours.cpue()).isNull();
        assertThat(zeroHours.landingRate()).isEqualTo(0.0);
    }

    @Test
    void guidanceScoreUsesFishOnNotLanded() {
        GuidanceProperties.Empirical cfg = new GuidanceProperties.Empirical();
        EmpiricalAlgorithm.Derived fishOnLost = EmpiricalAlgorithm.derive(new EmpiricalRawCounts(7200, 2, 2, 0, 1), cfg);
        EmpiricalAlgorithm.Derived noHook = EmpiricalAlgorithm.derive(new EmpiricalRawCounts(7200, 2, 0, 0, 1), cfg);
        EmpiricalAlgorithm.Derived landedOnly = EmpiricalAlgorithm.derive(new EmpiricalRawCounts(7200, 0, 0, 2, 1), cfg);

        assertThat(fishOnLost.smoothedScore()).isGreaterThan(noHook.smoothedScore());
        assertThat(fishOnLost.smoothedScore()).isGreaterThan(landedOnly.smoothedScore());
        assertThat(fishOnLost.landingRate()).isEqualTo(0.0);
        assertThat(fishOnLost.cpue()).isEqualTo(0.0);
    }
}
