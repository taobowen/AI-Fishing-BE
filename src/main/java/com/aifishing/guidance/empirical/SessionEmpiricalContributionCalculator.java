package com.aifishing.guidance.empirical;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.SeasonBucket;
import com.aifishing.guidance.contracts.SessionEventType;
import com.aifishing.guidance.contracts.TimeBucket;
import com.aifishing.guidance.contracts.WindBucket;
import com.aifishing.guidance.contracts.WindDirectionBucket;
import com.aifishing.guidance.persistence.LureEventEntity;
import com.aifishing.guidance.persistence.LureEventRepository;
import com.aifishing.guidance.persistence.SessionEventEntity;
import com.aifishing.guidance.persistence.SessionEventRepository;
import com.aifishing.guidance.persistence.WeatherSnapshotEntity;
import com.aifishing.guidance.persistence.WeatherSnapshotRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SessionEmpiricalContributionCalculator {

    private static final EnumSet<SessionEventType> FISH_SIGNALS =
            EnumSet.of(SessionEventType.BITE, SessionEventType.FISH_ON);

    private final FishingSessionRepository sessionRepository;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final FishingEffortSegmentRepository segmentRepository;
    private final CatchEventRepository catchEventRepository;
    private final SessionEventRepository sessionEventRepository;
    private final LureEventRepository lureEventRepository;
    private final WeatherSnapshotRepository weatherSnapshotRepository;

    public SessionEmpiricalContributionCalculator(
            FishingSessionRepository sessionRepository,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            TripWaypointRepository tripWaypointRepository,
            SessionWaypointProgressRepository progressRepository,
            FishingEffortSegmentRepository segmentRepository,
            CatchEventRepository catchEventRepository,
            SessionEventRepository sessionEventRepository,
            LureEventRepository lureEventRepository,
            WeatherSnapshotRepository weatherSnapshotRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.progressRepository = progressRepository;
        this.segmentRepository = segmentRepository;
        this.catchEventRepository = catchEventRepository;
        this.sessionEventRepository = sessionEventRepository;
        this.lureEventRepository = lureEventRepository;
        this.weatherSnapshotRepository = weatherSnapshotRepository;
    }

    public Calculated calculate(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return Calculated.empty();
        }
        Trip trip = tripRepository.findById(session.getTripId()).orElse(null);
        if (trip == null) {
            return Calculated.empty();
        }
        Lake lake = lakeRepository.findById(trip.getLakeId()).orElse(null);
        ZoneId zone = EmpiricalAlgorithm.zoneOf(lake == null ? null : lake.getTimeZoneId());
        FishSpecies species = trip.getPrimaryTargetSpecies();
        Map<UUID, TripWaypoint> waypoints = indexWaypoints(session.getTripPlanId());
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(sessionId);
        List<FishingEffortSegment> segments =
                segmentRepository.findByFishingSessionIdAndSegmentTypeOrderByStartedAtAsc(
                        sessionId, EffortSegmentType.FISHING);
        List<LureEventEntity> lures = lureEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);
        List<WeatherSnapshotEntity> weather =
                weatherSnapshotRepository.findByFishingSessionIdOrderByObservedAtAsc(sessionId);
        List<SessionEventEntity> events =
                sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(sessionId, FISH_SIGNALS);
        List<SessionEventEntity> waypointEnters =
                sessionEventRepository.findByFishingSessionIdAndTypeInOrderByOccurredAtAsc(
                        sessionId, EnumSet.of(SessionEventType.WAYPOINT_ENTERED));
        List<CatchEvent> catches = catchEventRepository.findByFishingSessionIdOrderByOccurredAtAsc(sessionId);

        Map<EmpiricalGrain, MutableCounts> byGrain = new LinkedHashMap<>();
        for (FishingEffortSegment segment : segments) {
            addEffort(byGrain, segment, trip.getLakeId(), species, zone, waypoints, lures, weather);
        }
        for (SessionEventEntity event : events) {
            Context context = contextAt(
                    event.getOccurredAt(),
                    trip.getLakeId(),
                    species,
                    zone,
                    segments,
                    progress,
                    waypoints,
                    waypointEnters,
                    lures,
                    weather
            );
            MutableCounts counts = byGrain.computeIfAbsent(context.grain(), key -> new MutableCounts());
            if (event.getType() == SessionEventType.BITE) {
                counts.bites++;
            } else if (event.getType() == SessionEventType.FISH_ON) {
                counts.fishOn++;
            }
        }
        for (CatchEvent catchEvent : catches) {
            if (catchEvent.getStatus() == CatchStatus.VOIDED || catchEvent.getOutcome() != CatchOutcome.LANDED) {
                continue;
            }
            FishSpecies landedSpecies = catchEvent.getSpecies() == null ? species : catchEvent.getSpecies();
            Context context = contextAt(
                    catchEvent.getOccurredAt(),
                    trip.getLakeId(),
                    landedSpecies,
                    zone,
                    segments,
                    progress,
                    waypoints,
                    waypointEnters,
                    lures,
                    weather
            );
            EmpiricalGrain grain = withCatchLocation(context.grain(), catchEvent, waypoints);
            byGrain.computeIfAbsent(grain, key -> new MutableCounts()).landed++;
        }

        List<SessionContribution> contributions = new ArrayList<>();
        for (Map.Entry<EmpiricalGrain, MutableCounts> entry : byGrain.entrySet()) {
            MutableCounts counts = entry.getValue();
            if (counts.effortSeconds == 0 && counts.bites == 0 && counts.fishOn == 0 && counts.landed == 0) {
                continue;
            }
            int waypointsCount = counts.effortSeconds > 0 || counts.bites > 0 || counts.fishOn > 0 || counts.landed > 0
                    ? 1
                    : 0;
            contributions.add(new SessionContribution(
                    entry.getKey(),
                    new EmpiricalRawCounts(counts.effortSeconds, counts.bites, counts.fishOn, counts.landed, waypointsCount)
            ));
        }
        contributions.sort(Comparator.comparing(SessionContribution::grain));
        return new Calculated(contributions, sourceHash(contributions));
    }

    private void addEffort(
            Map<EmpiricalGrain, MutableCounts> byGrain,
            FishingEffortSegment segment,
            UUID lakeId,
            FishSpecies species,
            ZoneId zone,
            Map<UUID, TripWaypoint> waypoints,
            List<LureEventEntity> lures,
            List<WeatherSnapshotEntity> weather
    ) {
        Instant cursor = segment.getStartedAt();
        Instant end = segment.getEndedAt();
        if (cursor == null || end == null || !end.isAfter(cursor)) {
            return;
        }
        while (cursor.isBefore(end)) {
            Instant next = EmpiricalAlgorithm.nextTimeBucketStart(cursor, zone);
            Instant sliceEnd = next.isBefore(end) ? next : end;
            long seconds = Math.max(0, sliceEnd.getEpochSecond() - cursor.getEpochSecond());
            if (seconds > 0) {
                EmpiricalGrain grain = grainOf(
                        lakeId,
                        species,
                        zone,
                        cursor,
                        segment.getTripWaypointId(),
                        segment.getZoneId(),
                        waypoints,
                        lureAt(lures, cursor),
                        weatherIn(weather, cursor, sliceEnd)
                );
                byGrain.computeIfAbsent(grain, key -> new MutableCounts()).effortSeconds += seconds;
            }
            cursor = sliceEnd;
        }
    }

    private Context contextAt(
            Instant at,
            UUID lakeId,
            FishSpecies species,
            ZoneId zone,
            List<FishingEffortSegment> segments,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            List<SessionEventEntity> waypointEnters,
            List<LureEventEntity> lures,
            List<WeatherSnapshotEntity> weather
    ) {
        Instant when = at == null ? Instant.EPOCH : at;
        UUID waypointId = waypointAt(when, segments, progress, waypointEnters);
        UUID zoneId = null;
        if (waypointId != null) {
            for (FishingEffortSegment segment : segments) {
                if (waypointId.equals(segment.getTripWaypointId()) && contains(segment, when)) {
                    zoneId = segment.getZoneId();
                    break;
                }
            }
        }
        EmpiricalGrain grain = grainOf(
                lakeId,
                species,
                zone,
                when,
                waypointId,
                zoneId,
                waypoints,
                lureAt(lures, when),
                weatherNear(weather, when)
        );
        return new Context(grain);
    }

    private static EmpiricalGrain grainOf(
            UUID lakeId,
            FishSpecies species,
            ZoneId zone,
            Instant at,
            UUID waypointId,
            UUID zoneId,
            Map<UUID, TripWaypoint> waypoints,
            LureFamily lureFamily,
            List<WeatherSnapshotEntity> weatherWindow
    ) {
        TripWaypoint waypoint = waypointId == null ? null : waypoints.get(waypointId);
        UUID resolvedZone = zoneId != null ? zoneId : waypoint == null ? null : waypoint.getZoneId();
        FeatureType structure = waypoint == null ? null : waypoint.getFeatureType();
        SeasonBucket season = EmpiricalAlgorithm.seasonBucket(at, zone);
        TimeBucket time = EmpiricalAlgorithm.timeBucket(at, zone);
        WindBucket wind = EmpiricalAlgorithm.windBucket(windSpeed(weatherWindow));
        WindDirectionBucket windDir = EmpiricalAlgorithm.windDirection(windDirections(weatherWindow));
        return new EmpiricalGrain(
                lakeId,
                resolvedZone,
                waypointId,
                species,
                season,
                time,
                structure,
                wind,
                windDir,
                lureFamily,
                EmpiricalAlgorithm.VERSION
        );
    }

    private static EmpiricalGrain withCatchLocation(
            EmpiricalGrain grain,
            CatchEvent catchEvent,
            Map<UUID, TripWaypoint> waypoints
    ) {
        UUID waypointId = catchEvent.getTripWaypointId() != null ? catchEvent.getTripWaypointId() : grain.tripWaypointId();
        UUID zoneId = catchEvent.getZoneId() != null ? catchEvent.getZoneId() : grain.zoneId();
        FeatureType structure = catchEvent.getPlannedFeatureType() != null
                ? catchEvent.getPlannedFeatureType()
                : grain.structure();
        if (structure == null && waypointId != null) {
            TripWaypoint waypoint = waypoints.get(waypointId);
            structure = waypoint == null ? null : waypoint.getFeatureType();
        }
        return new EmpiricalGrain(
                grain.lakeId(),
                zoneId,
                waypointId,
                grain.species(),
                grain.seasonBucket(),
                grain.timeBucket(),
                structure,
                grain.windBucket(),
                grain.windDirectionBucket(),
                grain.lureFamily(),
                grain.algorithmVersion()
        );
    }

    private static UUID waypointAt(
            Instant at,
            List<FishingEffortSegment> segments,
            List<SessionWaypointProgress> progress,
            List<SessionEventEntity> waypointEnters
    ) {
        for (FishingEffortSegment segment : segments) {
            if (contains(segment, at) && segment.getTripWaypointId() != null) {
                return segment.getTripWaypointId();
            }
        }
        for (SessionWaypointProgress row : progress) {
            if (row.getArrivedAt() != null
                    && !at.isBefore(row.getArrivedAt())
                    && (row.getDepartedAt() == null || at.isBefore(row.getDepartedAt()))) {
                return row.getTripWaypointId();
            }
        }
        UUID entered = null;
        for (SessionEventEntity event : waypointEnters) {
            if (event.getOccurredAt() != null && !event.getOccurredAt().isAfter(at)) {
                Object raw = event.getPayload() == null ? null : event.getPayload().get("waypointId");
                if (raw != null) {
                    try {
                        entered = UUID.fromString(String.valueOf(raw));
                    } catch (IllegalArgumentException ignored) {
                        // skip malformed payload
                    }
                }
            }
        }
        return entered;
    }

    private static boolean contains(FishingEffortSegment segment, Instant at) {
        return segment.getStartedAt() != null
                && segment.getEndedAt() != null
                && !at.isBefore(segment.getStartedAt())
                && at.isBefore(segment.getEndedAt());
    }

    private Map<UUID, TripWaypoint> indexWaypoints(UUID tripPlanId) {
        if (tripPlanId == null) {
            return Map.of();
        }
        Map<UUID, TripWaypoint> index = new LinkedHashMap<>();
        for (TripWaypoint waypoint : tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(tripPlanId)) {
            index.put(waypoint.getId(), waypoint);
        }
        return index;
    }

    private static LureFamily lureAt(List<LureEventEntity> lures, Instant at) {
        LureFamily latest = null;
        for (LureEventEntity lure : lures) {
            if (lure.getOccurredAt() != null && !lure.getOccurredAt().isAfter(at)) {
                latest = lure.getLureFamily();
            }
        }
        return latest;
    }

    private static List<WeatherSnapshotEntity> weatherIn(
            List<WeatherSnapshotEntity> weather,
            Instant start,
            Instant end
    ) {
        List<WeatherSnapshotEntity> window = new ArrayList<>();
        WeatherSnapshotEntity previous = null;
        for (WeatherSnapshotEntity snapshot : weather) {
            Instant observed = snapshot.getObservedAt();
            if (observed == null) {
                continue;
            }
            if (observed.isBefore(start)) {
                previous = snapshot;
                continue;
            }
            if (!observed.isBefore(end)) {
                break;
            }
            window.add(snapshot);
        }
        if (window.isEmpty() && previous != null) {
            window.add(previous);
        }
        return window;
    }

    private static List<WeatherSnapshotEntity> weatherNear(List<WeatherSnapshotEntity> weather, Instant at) {
        WeatherSnapshotEntity latest = null;
        for (WeatherSnapshotEntity snapshot : weather) {
            if (snapshot.getObservedAt() != null && !snapshot.getObservedAt().isAfter(at)) {
                latest = snapshot;
            }
        }
        return latest == null ? List.of() : List.of(latest);
    }

    private static Double windSpeed(List<WeatherSnapshotEntity> weather) {
        for (int i = weather.size() - 1; i >= 0; i--) {
            if (weather.get(i).getWindSpeedKph() != null) {
                return weather.get(i).getWindSpeedKph().doubleValue();
            }
        }
        return null;
    }

    private static List<CompassDirection> windDirections(List<WeatherSnapshotEntity> weather) {
        List<CompassDirection> directions = new ArrayList<>();
        for (WeatherSnapshotEntity snapshot : weather) {
            if (snapshot.getWindDirection() != null) {
                directions.add(snapshot.getWindDirection());
            }
        }
        return directions;
    }

    static String sourceHash(List<SessionContribution> contributions) {
        StringBuilder canonical = new StringBuilder();
        for (SessionContribution contribution : contributions) {
            EmpiricalGrain grain = contribution.grain();
            EmpiricalRawCounts raw = contribution.raw();
            canonical.append(grain.lakeId()).append('|')
                    .append(grain.zoneId()).append('|')
                    .append(grain.tripWaypointId()).append('|')
                    .append(grain.species()).append('|')
                    .append(grain.seasonBucket()).append('|')
                    .append(grain.timeBucket()).append('|')
                    .append(grain.structure()).append('|')
                    .append(grain.windBucket()).append('|')
                    .append(grain.windDirectionBucket()).append('|')
                    .append(grain.lureFamily()).append('|')
                    .append(grain.algorithmVersion()).append('|')
                    .append(raw.fishingEffortSeconds()).append('|')
                    .append(raw.biteCount()).append('|')
                    .append(raw.fishOnCount()).append('|')
                    .append(raw.landedCount()).append('|')
                    .append(raw.contributingSessionWaypointCount())
                    .append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Calculated(List<SessionContribution> contributions, String sourceHash) {
        static Calculated empty() {
            return new Calculated(List.of(), SessionEmpiricalContributionCalculator.sourceHash(List.of()));
        }
    }

    private record Context(EmpiricalGrain grain) {
    }

    private static final class MutableCounts {
        private long effortSeconds;
        private int bites;
        private int fishOn;
        private int landed;
    }
}
