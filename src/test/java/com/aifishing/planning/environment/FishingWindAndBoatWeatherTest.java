package com.aifishing.planning.environment;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.domain.Lake;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishingWindAndBoatWeatherTest {

    private final BoatWeatherPenalty penalty = new BoatWeatherPenalty();

    @Test
    void westWindIsWindwardOnEastShoreAndLeewardOnWestShore() {
        WeatherSample westWind = new WeatherSample(
                Instant.parse("2026-09-12T12:00:00Z"),
                18.0,
                270.0,
                AzimuthConvention.flowFromMeteorological(270),
                20.0,
                16.0,
                0.0,
                1013.0,
                600.0,
                600.0
        );
        LocalOrientation eastShore = new LocalOrientation(270.0, null, 10.0, false, OrientationConfidence.HIGH, "TEST");
        LocalOrientation westShore = new LocalOrientation(90.0, null, 10.0, false, OrientationConfidence.HIGH, "TEST");
        assertThat(FishingWindEffect.classify(westWind, eastShore)).isEqualTo(WindOrientation.WINDWARD);
        assertThat(FishingWindEffect.classify(westWind, westShore)).isEqualTo(WindOrientation.LEEWARD);
        assertThat(FishingWindEffect.effect(WindOrientation.WINDWARD, 0.08))
                .isEqualTo(FishingWindEffect.effect(WindOrientation.WINDWARD, 0.08));
    }

    @Test
    void fishingWindIsBoatAgnosticAndBoatPenaltyIsNot() {
        double fishing = FishingWindEffect.effect(WindOrientation.LEEWARD, 0.08);
        PlanningContext boat = context(FishingMode.BOAT);
        WeatherSample hard = new WeatherSample(
                Instant.parse("2026-09-12T12:00:00Z"),
                42.0, 270.0, 90.0, 80.0, 12.0, 0.0, 1013.0, 100.0, 100.0);
        WeatherSample calm = new WeatherSample(
                Instant.parse("2026-09-12T13:00:00Z"),
                8.0, 270.0, 90.0, 20.0, 12.0, 0.0, 1013.0, 500.0, 500.0);
        assertThat(fishing).isGreaterThan(0);
        assertThat(penalty.hardReject(hard, boat)).isTrue();
        assertThat(penalty.hardReject(calm, boat)).isFalse();
        assertThat(penalty.hardReject(hard, context(FishingMode.SHORE))).isFalse();
    }

    @Test
    void travelIntervalRejectsStormyTransitEvenIfDestinationIsCalm() {
        TimeIndexedWeather weather = TimeIndexedWeather.from(stormThenCalm(), ZoneId.of("America/Toronto"));
        Instant depart = ZonedDateTime.of(2026, 9, 12, 8, 0, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        Instant arrive = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        assertThat(penalty.travelIntervalHardReject(weather, depart, arrive, context(FishingMode.BOAT))).isTrue();
        Instant laterDepart = ZonedDateTime.of(2026, 9, 12, 9, 0, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        Instant laterArrive = ZonedDateTime.of(2026, 9, 12, 9, 20, 0, 0, ZoneId.of("America/Toronto")).toInstant();
        assertThat(penalty.travelIntervalHardReject(weather, laterDepart, laterArrive, context(FishingMode.BOAT))).isFalse();
    }

    private static com.aifishing.strategy.weather.WeatherContext stormThenCalm() {
        return new com.aifishing.strategy.weather.WeatherContext(
                com.aifishing.strategy.weather.WeatherAvailability.FORECAST_AVAILABLE,
                Instant.parse("2026-09-02T16:00:00Z"),
                "open-meteo",
                "America/Toronto",
                LocalDate.of(2026, 9, 12),
                false,
                null,
                14.0,
                42.0,
                270.0,
                0.0,
                80.0,
                1013.0,
                LocalTime.of(6, 42),
                LocalTime.of(19, 31),
                List.of(
                        new com.aifishing.strategy.weather.WeatherContext.HourlyWeather(
                                LocalTime.of(8, 0), 12.0, 42.0, 270.0, 0.0, 90.0, 1013.0, 50.0, 50.0),
                        new com.aifishing.strategy.weather.WeatherContext.HourlyWeather(
                                LocalTime.of(9, 0), 12.0, 8.0, 270.0, 0.0, 20.0, 1013.0, 600.0, 600.0)
                ),
                "air"
        );
    }

    private static PlanningContext context(FishingMode mode) {
        Trip trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), mode);
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        lake.setTimeZoneId("America/Toronto");
        return new PlanningContext(
                trip,
                lake,
                null,
                AccessResolution.unknown(),
                new LakePlanningGeometry(null, List.of()),
                List.of(),
                "AVAILABLE",
                PlanningFixtures.forecast(),
                List.of(),
                StrategyFixtures.validProfile(),
                new StrategyRun(),
                new PlanningProperties(),
                new ArrayList<>()
        );
    }
}
