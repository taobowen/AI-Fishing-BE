package com.aifishing.guidance.runtime;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.WeatherSnapshot;
import com.aifishing.guidance.persistence.WeatherSnapshotEntity;
import com.aifishing.guidance.persistence.WeatherSnapshotRepository;
import com.aifishing.guidance.spi.EnvironmentSnapshotResolver;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.strategy.weather.WeatherService;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fresh DB weather snapshot is reused; stale or missing rows refresh Open-Meteo via {@link WeatherService}
 * and append {@code weather_snapshots}. Redis can later replace only the lookup/cache here.
 */
@Component
public class PersistedEnvironmentSnapshotResolver implements EnvironmentSnapshotResolver {

    private final Clock clock;
    private final GuidanceProperties guidanceProperties;
    private final WeatherService weatherService;
    private final WeatherSnapshotRepository weatherSnapshotRepository;
    private final FishingSessionRepository sessionRepository;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final SessionLocationPointRepository locationPointRepository;

    public PersistedEnvironmentSnapshotResolver(
            Clock clock,
            GuidanceProperties guidanceProperties,
            WeatherService weatherService,
            WeatherSnapshotRepository weatherSnapshotRepository,
            FishingSessionRepository sessionRepository,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            SessionLocationPointRepository locationPointRepository
    ) {
        this.clock = clock;
        this.guidanceProperties = guidanceProperties;
        this.weatherService = weatherService;
        this.weatherSnapshotRepository = weatherSnapshotRepository;
        this.sessionRepository = sessionRepository;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.locationPointRepository = locationPointRepository;
    }

    @Override
    @Transactional
    public EnvironmentSnapshot resolve(UUID sessionId) {
        Instant now = clock.instant();
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        QueryPoint query = queryPoint(session);
        WeatherSnapshotEntity stored = weatherSnapshotRepository
                .findFirstByFishingSessionIdOrderByObservedAtDesc(sessionId)
                .orElse(null);
        Duration maxAge = Duration.ofMinutes(guidanceProperties.getWeatherSnapshotMaxAgeMinutes());
        if (stored != null && WeatherSnapshotFreshness.isFresh(stored.getObservedAt(), now, maxAge)) {
            WeatherSnapshot weather = toWeather(stored);
            return new EnvironmentSnapshot(
                    now,
                    weather,
                    false,
                    WeatherSnapshotFreshness.ageMinutes(weather, now),
                    query.latitudeWgs84(),
                    query.longitudeWgs84()
            );
        }
        Trip trip = tripRepository.findById(session.getTripId())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        ZoneId zone = zoneOf(lake.getTimeZoneId());
        LocalDate forecastDate = forecastDate(session, trip, zone, now);
        LocalTime[] window = forecastWindow(session, trip, zone, now);
        WeatherContext context = weatherService.forTrip(
                query.latitudeWgs84(),
                query.longitudeWgs84(),
                lake.getTimeZoneId(),
                forecastDate,
                window[0],
                window[1]
        );
        WeatherSnapshot weather = WeatherObservationMapper.fromContext(context, now);
        persist(sessionId, weather, query, context);
        return new EnvironmentSnapshot(
                now,
                weather,
                true,
                WeatherSnapshotFreshness.ageMinutes(weather, now),
                query.latitudeWgs84(),
                query.longitudeWgs84()
        );
    }

    private QueryPoint queryPoint(FishingSession session) {
        SessionLocationPoint gps = locationPointRepository
                .findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(session.getId(), LocationQuality.ACCEPTED)
                .or(() -> locationPointRepository.findFirstByFishingSessionIdOrderByRecordedAtDesc(session.getId()))
                .orElse(null);
        if (gps != null && gps.getLocation() != null) {
            return new QueryPoint(gps.getLocation().getY(), gps.getLocation().getX());
        }
        Trip trip = tripRepository.findById(session.getTripId())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
        Lake lake = lakeRepository.findById(trip.getLakeId())
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        Point centroid = lake.getCentroid();
        if (centroid == null) {
            throw new IllegalStateException("Lake centroid is required to resolve weather (WGS84)");
        }
        return new QueryPoint(centroid.getY(), centroid.getX());
    }

    private static LocalDate forecastDate(FishingSession session, Trip trip, ZoneId zone, Instant now) {
        LocalDate planned = trip.getPlannedDate();
        if (planned == null) {
            return LocalDate.ofInstant(now, zone);
        }
        if (session.getStatus() != null && session.getStatus().isUnfinished()) {
            LocalDate today = LocalDate.ofInstant(now, zone);
            return today.isBefore(planned) ? planned : today;
        }
        return planned;
    }

    private static LocalTime[] forecastWindow(FishingSession session, Trip trip, ZoneId zone, Instant now) {
        if (session.getStatus() != null && session.getStatus().isUnfinished()) {
            LocalTime local = LocalTime.ofInstant(now, zone);
            return new LocalTime[] { local.minusHours(1).truncatedTo(ChronoUnit.MINUTES), local.plusHours(1) };
        }
        return new LocalTime[] { trip.getFishingStartTime(), trip.getFishingEndTime() };
    }

    private static ZoneId zoneOf(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) {
            return ZoneId.of("UTC");
        }
        return ZoneId.of(timeZoneId);
    }

    private void persist(UUID sessionId, WeatherSnapshot weather, QueryPoint query, WeatherContext context) {
        WeatherSnapshotEntity entity = new WeatherSnapshotEntity();
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setFishingSessionId(sessionId);
        entity.setObservedAt(weather.observedAt());
        entity.setWindSpeedKph(decimal(weather.windSpeedKph()));
        entity.setWindDirection(weather.windDirection());
        entity.setTemperatureC(decimal(weather.temperatureC()));
        entity.setPressureHpa(decimal(weather.pressureHpa()));
        entity.setEnvelope(envelope(weather, query, context));
        weatherSnapshotRepository.save(entity);
    }

    private static Map<String, Object> envelope(WeatherSnapshot weather, QueryPoint query, WeatherContext context) {
        Map<String, Object> envelope = new LinkedHashMap<>(GuidanceContracts.mapper().convertValue(weather, Map.class));
        envelope.put("queryLatitudeWgs84", query.latitudeWgs84());
        envelope.put("queryLongitudeWgs84", query.longitudeWgs84());
        if (context != null) {
            envelope.put("provider", context.provider());
            envelope.put("availability", context.availability() == null ? null : context.availability().name());
            envelope.put("cloudCoverPercent", context.cloudCoverPercent());
            envelope.put("precipitationMm", context.precipitationMm());
        }
        return envelope;
    }

    private static WeatherSnapshot toWeather(WeatherSnapshotEntity entity) {
        if (entity.getEnvelope() != null && !entity.getEnvelope().isEmpty()) {
            try {
                Map<String, Object> weatherOnly = new LinkedHashMap<>();
                for (String key : List.of(
                        "schemaVersion", "observedAt", "weather", "windSpeedKph",
                        "windDirection", "temperatureC", "pressureHpa"
                )) {
                    if (entity.getEnvelope().containsKey(key)) {
                        weatherOnly.put(key, entity.getEnvelope().get(key));
                    }
                }
                WeatherSnapshot fromEnvelope = GuidanceContracts.mapper()
                        .convertValue(weatherOnly, WeatherSnapshot.class);
                if (fromEnvelope != null && fromEnvelope.observedAt() != null) {
                    return fromEnvelope;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to column mapping
            }
        }
        return new WeatherSnapshot(
                entity.getSchemaVersion(),
                entity.getObservedAt(),
                null,
                entity.getWindSpeedKph() == null ? null : entity.getWindSpeedKph().doubleValue(),
                entity.getWindDirection(),
                entity.getTemperatureC() == null ? null : entity.getTemperatureC().doubleValue(),
                entity.getPressureHpa() == null ? null : entity.getPressureHpa().doubleValue()
        );
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private record QueryPoint(double latitudeWgs84, double longitudeWgs84) {
    }
}
