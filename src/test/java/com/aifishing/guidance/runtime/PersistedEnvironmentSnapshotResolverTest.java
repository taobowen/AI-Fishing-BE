package com.aifishing.guidance.runtime;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.guidance.persistence.WeatherSnapshotEntity;
import com.aifishing.guidance.persistence.WeatherSnapshotRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.strategy.weather.WeatherService;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistedEnvironmentSnapshotResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TRIP_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LAKE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private WeatherService weatherService;
    @Mock
    private WeatherSnapshotRepository weatherSnapshotRepository;
    @Mock
    private FishingSessionRepository sessionRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private LakeRepository lakeRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;

    private PersistedEnvironmentSnapshotResolver resolver;

    @BeforeEach
    void setUp() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setWeatherSnapshotMaxAgeMinutes(15);
        resolver = new PersistedEnvironmentSnapshotResolver(
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties,
                weatherService,
                weatherSnapshotRepository,
                sessionRepository,
                tripRepository,
                lakeRepository,
                locationPointRepository
        );
    }

    @Test
    void staleSnapshotRefreshesOpenMeteoAndPersists() {
        stubSessionGraph();
        WeatherSnapshotEntity stale = stored(NOW.minusSeconds(16 * 60));
        when(weatherSnapshotRepository.findFirstByFishingSessionIdOrderByObservedAtDesc(SESSION_ID))
                .thenReturn(Optional.of(stale));
        when(weatherService.forTrip(anyDouble(), anyDouble(), any(), any(), any(), any()))
                .thenReturn(PlanningFixtures.forecast());

        EnvironmentSnapshot snapshot = resolver.resolve(SESSION_ID);

        assertThat(snapshot.refreshed()).isTrue();
        assertThat(snapshot.weather().schemaVersion()).isEqualTo(GuidanceSchemaVersion.VALUE);
        assertThat(snapshot.queryLatitudeWgs84()).isEqualTo(44.75);
        assertThat(snapshot.queryLongitudeWgs84()).isEqualTo(-78.92);
        verify(weatherService).forTrip(
                eq(44.75),
                eq(-78.92),
                eq("America/Toronto"),
                any(LocalDate.class),
                any(LocalTime.class),
                any(LocalTime.class)
        );
        ArgumentCaptor<WeatherSnapshotEntity> captor = ArgumentCaptor.forClass(WeatherSnapshotEntity.class);
        verify(weatherSnapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getFishingSessionId()).isEqualTo(SESSION_ID);
        assertThat(captor.getValue().getObservedAt()).isNotNull();
    }

    @Test
    void missingSnapshotRefreshes() {
        stubSessionGraph();
        when(weatherSnapshotRepository.findFirstByFishingSessionIdOrderByObservedAtDesc(SESSION_ID))
                .thenReturn(Optional.empty());
        when(weatherService.forTrip(anyDouble(), anyDouble(), any(), any(), any(), any()))
                .thenReturn(PlanningFixtures.forecast());

        EnvironmentSnapshot snapshot = resolver.resolve(SESSION_ID);

        assertThat(snapshot.refreshed()).isTrue();
        verify(weatherService).forTrip(anyDouble(), anyDouble(), any(), any(), any(), any());
        verify(weatherSnapshotRepository).save(any(WeatherSnapshotEntity.class));
    }

    @Test
    void freshSnapshotSkipsWeatherServiceAndWrites() {
        stubSessionGraph();
        WeatherSnapshotEntity fresh = stored(NOW.minusSeconds(4 * 60));
        when(weatherSnapshotRepository.findFirstByFishingSessionIdOrderByObservedAtDesc(SESSION_ID))
                .thenReturn(Optional.of(fresh));

        EnvironmentSnapshot snapshot = resolver.resolve(SESSION_ID);

        assertThat(snapshot.refreshed()).isFalse();
        assertThat(snapshot.weatherAgeMinutes()).isEqualTo(4);
        assertThat(snapshot.weather().weather()).isEqualTo(WeatherCondition.CLOUDY);
        verify(weatherService, never()).forTrip(anyDouble(), anyDouble(), any(), any(), any(), any());
        verify(weatherSnapshotRepository, never()).save(any());
    }

    private void stubSessionGraph() {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setTripId(TRIP_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(locationPointRepository.findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(Optional.empty());
        when(locationPointRepository.findFirstByFishingSessionIdOrderByRecordedAtDesc(SESSION_ID))
                .thenReturn(Optional.empty());

        Trip trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setLakeId(LAKE_ID);
        trip.setPlannedDate(LocalDate.of(2026, 9, 12));
        trip.setFishingStartTime(LocalTime.of(6, 0));
        trip.setFishingEndTime(LocalTime.of(15, 0));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));

        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);
        Lake lake = new Lake();
        lake.setId(LAKE_ID);
        lake.setTimeZoneId("America/Toronto");
        lake.setCentroid(factory.createPoint(new Coordinate(-78.92, 44.75)));
        when(lakeRepository.findById(LAKE_ID)).thenReturn(Optional.of(lake));
    }

    private static WeatherSnapshotEntity stored(Instant observedAt) {
        WeatherSnapshot weather = new WeatherSnapshot(
                GuidanceSchemaVersion.VALUE,
                observedAt,
                WeatherCondition.CLOUDY,
                12.0,
                null,
                17.0,
                1015.0
        );
        WeatherSnapshotEntity entity = new WeatherSnapshotEntity();
        entity.setFishingSessionId(SESSION_ID);
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setObservedAt(observedAt);
        entity.setEnvelope(Map.of(
                "schemaVersion", weather.schemaVersion(),
                "observedAt", weather.observedAt().toString(),
                "weather", weather.weather().name(),
                "windSpeedKph", weather.windSpeedKph(),
                "temperatureC", weather.temperatureC(),
                "pressureHpa", weather.pressureHpa()
        ));
        return entity;
    }
}
