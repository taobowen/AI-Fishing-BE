package com.aifishing.planning.service;

import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.dto.WeatherPreviewRequest;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherPreviewServiceTest {

    @Mock
    private LakeRepository lakeRepository;
    @Mock
    private WeatherService weatherService;
    @InjectMocks
    private WeatherPreviewService service;

    @Test
    void successReturnsCanonicalSummaryAndFailureIsUnavailable() {
        Lake lake = new Lake();
        lake.setId(DevSeedIds.LAKE_ID);
        lake.setTimeZoneId("America/Toronto");
        lake.setCentroid(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(-78.92, 44.75)));
        when(lakeRepository.findById(DevSeedIds.LAKE_ID)).thenReturn(Optional.of(lake));
        LocalDate day = LocalDate.of(2026, 9, 20);
        when(weatherService.forTrip(anyDouble(), anyDouble(), any(), any(), any(), any(), any()))
                .thenReturn(new WeatherContext(
                        WeatherAvailability.FORECAST_AVAILABLE,
                        Instant.parse("2026-09-18T12:00:00Z"),
                        "open-meteo",
                        "America/Toronto",
                        day,
                        false,
                        null,
                        12.0,
                        10.0,
                        270.0,
                        0.0,
                        20.0,
                        1012.0,
                        LocalTime.of(6, 42),
                        LocalTime.of(19, 31),
                        List.of(new WeatherContext.HourlyWeather(
                                LocalTime.of(6, 0), 12.0, 10.0, 270.0, 0.0, 20.0, 1012.0, null, null, 1, day)),
                        "note"
                ));

        var available = service.preview(new WeatherPreviewRequest(
                DevSeedIds.LAKE_ID, day, day, LocalTime.of(6, 0), LocalTime.of(15, 0)));
        assertThat(available.available()).isTrue();
        assertThat(available.temperatureC()).isEqualTo(12.0);
        assertThat(available.windSpeedKmh()).isEqualTo(10.0);
        assertThat(available.weatherCode()).isEqualTo(1);

        when(weatherService.forTrip(anyDouble(), anyDouble(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("provider down"));
        var failed = service.preview(new WeatherPreviewRequest(
                DevSeedIds.LAKE_ID, day, day, LocalTime.of(6, 0), LocalTime.of(15, 0)));
        assertThat(failed.available()).isFalse();
    }
}
