package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.WindWaveCapability;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BoatCapabilityRangeMathTest {

    @Test
    void reserveAppliesOnlyToEstimatedPracticalRange() {
        Double system = BoatCapabilityRangeMath.systemUsableRangeKm(40.0, 0.30, true);
        assertThat(system).isCloseTo(28.0, within(1e-9));
        Double effective = BoatCapabilityRangeMath.effectiveUsableRangeKm(system, 20.0);
        assertThat(effective).isCloseTo(20.0, within(1e-9));
        assertThat(BoatCapabilityRangeMath.effectiveUsableRangeKm(system, 50.0)).isCloseTo(28.0, within(1e-9));
    }

    @Test
    void lowConfidenceDoesNotInventSystemRangeButHonorsComfortableCap() {
        assertThat(BoatCapabilityRangeMath.systemUsableRangeKm(40.0, 0.30, false)).isNull();
        assertThat(BoatCapabilityRangeMath.effectiveUsableRangeKm(null, 12.0)).isEqualTo(12.0);
    }

    @Test
    void weatherDerateDoesNotMultiplyUserCapByReserve() {
        Boat boat = new Boat();
        boat.setType(BoatType.FISHING_BOAT);
        boat.setComfortableRoundTripRangeKm(BigDecimal.valueOf(20));
        ResolvedBoatCapability baseline = new ResolvedBoatCapability(
                CapabilityMetric.of(20.0, 1.0, CapabilitySource.USER_OVERRIDE),
                CapabilityMetric.of(40.0, 0.8, CapabilitySource.AI_MODEL_ESTIMATED),
                CapabilityMetric.of(WindWaveCapability.LOW, 0.6, CapabilitySource.AI_MODEL_ESTIMATED),
                List.of(),
                List.of(),
                List.of(),
                null,
                "abc",
                "v1",
                Instant.now()
        );
        PlanningProperties planning = new PlanningProperties();
        WeatherContext calm = new WeatherContext(
                WeatherAvailability.FORECAST_AVAILABLE,
                Instant.now(),
                "test",
                "America/Toronto",
                LocalDate.now(),
                false,
                null,
                10.0,
                10.0,
                180.0,
                0.0,
                50.0,
                1013.0,
                LocalTime.of(6, 0),
                LocalTime.of(19, 0),
                List.of(),
                "air"
        );
        EffectiveBoatCapabilityFactory factory = new EffectiveBoatCapabilityFactory(new BoatCapabilityProperties());
        EffectiveBoatCapability effective = factory.from(boat, baseline, calm, planning);
        assertThat(effective.systemUsableRangeKm()).isCloseTo(28.0, within(1e-9));
        assertThat(effective.comfortableCapKm()).isEqualTo(20.0);
        assertThat(effective.effectiveUsableRangeKm()).isEqualTo(20.0);
    }
}
