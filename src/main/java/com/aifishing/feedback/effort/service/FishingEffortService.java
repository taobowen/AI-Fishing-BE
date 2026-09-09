package com.aifishing.feedback.effort.service;

import com.aifishing.common.enums.TechniqueType;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.feedback.FeedbackProperties;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.domain.SessionPauseInterval;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.feedback.effort.repo.SessionPauseIntervalRepository;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.repo.FishingSessionRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.repo.TripWaypointRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FishingEffortService {

    private final FishingSessionRepository sessionRepository;
    private final SessionLocationPointRepository locationPointRepository;
    private final SessionWaypointProgressRepository progressRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final SessionPauseIntervalRepository pauseIntervalRepository;
    private final FishingEffortSegmentRepository segmentRepository;
    private final SessionProperties sessionProperties;
    private final FeedbackProperties feedbackProperties;
    private final GeoMapper geoMapper;

    public FishingEffortService(
            FishingSessionRepository sessionRepository,
            SessionLocationPointRepository locationPointRepository,
            SessionWaypointProgressRepository progressRepository,
            TripWaypointRepository tripWaypointRepository,
            SessionPauseIntervalRepository pauseIntervalRepository,
            FishingEffortSegmentRepository segmentRepository,
            SessionProperties sessionProperties,
            FeedbackProperties feedbackProperties,
            GeoMapper geoMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.locationPointRepository = locationPointRepository;
        this.progressRepository = progressRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.pauseIntervalRepository = pauseIntervalRepository;
        this.segmentRepository = segmentRepository;
        this.sessionProperties = sessionProperties;
        this.feedbackProperties = feedbackProperties;
        this.geoMapper = geoMapper;
    }

    @Transactional
    public void recompute(UUID sessionId) {
        FishingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Fishing session not found"));
        segmentRepository.deleteByFishingSessionId(sessionId);
        List<FishingEffortSegment> segments = derive(session);
        if (!segments.isEmpty()) {
            segmentRepository.saveAll(segments);
        }
    }

    private List<FishingEffortSegment> derive(FishingSession session) {
        Instant end = session.getEndedAt() == null ? Instant.now() : session.getEndedAt();
        List<SessionLocationPoint> accepted = locationPointRepository
                .findByFishingSessionIdAndQualityOrderByRecordedAtAsc(session.getId(), LocationQuality.ACCEPTED);
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        Map<UUID, TripWaypoint> waypoints = new HashMap<>();
        if (session.getTripPlanId() != null) {
            for (TripWaypoint waypoint : tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(session.getTripPlanId())) {
                waypoints.put(waypoint.getId(), waypoint);
            }
        }
        List<SessionPauseInterval> pauses =
                pauseIntervalRepository.findByFishingSessionIdOrderByPausedAtAsc(session.getId());
        List<Draft> drafts = new ArrayList<>();
        int maxGap = feedbackProperties.getEffort().getMaxSampleGapSeconds();

        for (int i = 0; i + 1 < accepted.size(); i++) {
            SessionLocationPoint a = accepted.get(i);
            SessionLocationPoint b = accepted.get(i + 1);
            Instant t0 = a.getRecordedAt();
            Instant t1 = b.getRecordedAt();
            if (t1.isBefore(t0) || !t1.isAfter(t0)) {
                continue;
            }
            if (paused(pauses, t0.plusMillis(Duration.between(t0, t1).toMillis() / 2))) {
                continue;
            }
            long seconds = Duration.between(t0, t1).getSeconds();
            if (seconds <= 0) {
                continue;
            }
            double speed = GeoMetrics.distanceM(a.getLocation(), b.getLocation()) / Math.max(1.0, seconds);
            if (seconds > maxGap) {
                drafts.add(draft(session, progress, waypoints, EffortSegmentType.UNKNOWN, t0, t1, List.of(a, b), 0.4));
                continue;
            }
            TripWaypoint fishingWp = fishingWaypoint(progress, waypoints, b.getLocation(), t1, speed);
            if (fishingWp != null) {
                drafts.add(draftForWaypoint(session, fishingWp, EffortSegmentType.FISHING, t0, t1, List.of(a, b), 1.0));
            } else if (navigating(progress, t1) || outsideFishingRadius(progress, waypoints, b.getLocation(), t1)) {
                drafts.add(draft(session, progress, waypoints, EffortSegmentType.TRAVEL, t0, t1, List.of(a, b), 0.8));
            } else {
                drafts.add(draft(session, progress, waypoints, EffortSegmentType.UNKNOWN, t0, t1, List.of(a, b), 0.5));
            }
        }

        addWaypointOnlyFishing(session, accepted, progress, waypoints, pauses, end, drafts);
        return merge(session, drafts);
    }

    private void addWaypointOnlyFishing(
            FishingSession session,
            List<SessionLocationPoint> accepted,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            List<SessionPauseInterval> pauses,
            Instant sessionEnd,
            List<Draft> drafts
    ) {
        for (SessionWaypointProgress row : progress) {
            if (row.getArrivedAt() == null) {
                continue;
            }
            Instant departed = firstNonNull(row.getDepartedAt(), row.getCompletedAt(), row.getSkippedAt(), sessionEnd);
            if (departed == null || !departed.isAfter(row.getArrivedAt())) {
                continue;
            }
            boolean gpsInWindow = accepted.stream().anyMatch(point ->
                    !point.getRecordedAt().isBefore(row.getArrivedAt()) && !point.getRecordedAt().isAfter(departed));
            if (gpsInWindow) {
                continue;
            }
            if (paused(pauses, row.getArrivedAt().plusMillis(Duration.between(row.getArrivedAt(), departed).toMillis() / 2))) {
                continue;
            }
            TripWaypoint waypoint = waypoints.get(row.getTripWaypointId());
            drafts.add(draftForWaypoint(
                    session,
                    waypoint,
                    EffortSegmentType.FISHING,
                    row.getArrivedAt(),
                    departed,
                    List.of(),
                    0.6
            ));
        }
    }

    private TripWaypoint fishingWaypoint(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            Point location,
            Instant at,
            double speedMps
    ) {
        double radius = sessionProperties.getWaypoint().getDepartureRadiusM();
        for (SessionWaypointProgress row : progress) {
            if (!arrivedFishingAt(row, at)) {
                continue;
            }
            TripWaypoint waypoint = waypoints.get(row.getTripWaypointId());
            if (waypoint == null || waypoint.getLocation() == null || location == null) {
                continue;
            }
            if (GeoMetrics.distanceM(location, waypoint.getLocation()) > radius) {
                continue;
            }
            if (isTrolling(waypoint) && speedMps > feedbackProperties.getEffort().getMaxTrollingSpeedMps()) {
                continue;
            }
            return waypoint;
        }
        return null;
    }

    private boolean navigating(List<SessionWaypointProgress> progress, Instant at) {
        return progress.stream().anyMatch(row -> navigatingAt(row, at));
    }

    private boolean outsideFishingRadius(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            Point location,
            Instant at
    ) {
        double radius = sessionProperties.getWaypoint().getDepartureRadiusM();
        for (SessionWaypointProgress row : progress) {
            if (!arrivedFishingAt(row, at)) {
                continue;
            }
            TripWaypoint waypoint = waypoints.get(row.getTripWaypointId());
            if (waypoint != null && waypoint.getLocation() != null && location != null
                    && GeoMetrics.distanceM(location, waypoint.getLocation()) <= radius) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrivedFishingAt(SessionWaypointProgress row, Instant at) {
        if (row.getArrivedAt() == null || at.isBefore(row.getArrivedAt())) {
            return false;
        }
        Instant left = firstNonNull(row.getDepartedAt(), row.getCompletedAt(), row.getSkippedAt());
        return left == null || at.isBefore(left);
    }

    private static boolean navigatingAt(SessionWaypointProgress row, Instant at) {
        if (arrivedFishingAt(row, at)) {
            return false;
        }
        if (row.getSkippedAt() != null && !at.isBefore(row.getSkippedAt())) {
            return false;
        }
        if (row.getCompletedAt() != null && !at.isBefore(row.getCompletedAt())) {
            return false;
        }
        if (row.getFirstApproachedAt() != null && !at.isBefore(row.getFirstApproachedAt())) {
            return true;
        }
        return row.getSequence() == 1;
    }

    private static boolean isTrolling(TripWaypoint waypoint) {
        List<String> techniques = waypoint.getRecommendedTechniques();
        if (techniques == null) {
            return TechniqueType.TROLLING.name().equals(waypoint.getRecommendedTechnique());
        }
        return techniques.stream().anyMatch(value -> TechniqueType.TROLLING.name().equals(value));
    }

    private static boolean paused(List<SessionPauseInterval> pauses, Instant at) {
        for (SessionPauseInterval pause : pauses) {
            if (at.isBefore(pause.getPausedAt())) {
                continue;
            }
            if (pause.getResumedAt() == null || at.isBefore(pause.getResumedAt())) {
                return true;
            }
        }
        return false;
    }

    private Draft draft(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            EffortSegmentType type,
            Instant start,
            Instant end,
            List<SessionLocationPoint> points,
            double confidence
    ) {
        TripWaypoint waypoint = null;
        if (type == EffortSegmentType.FISHING) {
            Instant mid = start.plusMillis(Duration.between(start, end).toMillis() / 2);
            Point loc = points.isEmpty() ? null : points.get(points.size() - 1).getLocation();
            waypoint = fishingWaypoint(progress, waypoints, loc, mid, 0);
        }
        return draftForWaypoint(session, waypoint, type, start, end, points, confidence);
    }

    private Draft draftForWaypoint(
            FishingSession session,
            TripWaypoint waypoint,
            EffortSegmentType type,
            Instant start,
            Instant end,
            List<SessionLocationPoint> points,
            double confidence
    ) {
        List<Point> geometry = points.stream().map(SessionLocationPoint::getLocation).toList();
        return new Draft(
                session.getId(),
                waypoint == null ? null : waypoint.getId(),
                waypoint == null ? null : waypoint.getLakeFeatureId(),
                waypoint == null ? null : waypoint.getZoneId(),
                waypoint != null && waypoint.getZoneId() == null ? waypoint.getFishingTargetId() : null,
                type,
                start,
                end,
                geometry,
                confidence
        );
    }

    private List<FishingEffortSegment> merge(FishingSession session, List<Draft> drafts) {
        if (drafts.isEmpty()) {
            return List.of();
        }
        drafts.sort((a, b) -> a.start.compareTo(b.start));
        List<Draft> merged = new ArrayList<>();
        Draft current = drafts.get(0);
        for (int i = 1; i < drafts.size(); i++) {
            Draft next = drafts.get(i);
            if (current.type == next.type
                    && java.util.Objects.equals(current.tripWaypointId, next.tripWaypointId)
                    && current.end.equals(next.start)) {
                current = current.merge(next);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        String version = feedbackProperties.getEffort().getDerivationVersion();
        List<FishingEffortSegment> out = new ArrayList<>();
        for (Draft draft : merged) {
            FishingEffortSegment segment = new FishingEffortSegment();
            segment.setFishingSessionId(session.getId());
            segment.setTripWaypointId(draft.tripWaypointId);
            segment.setLakeFeatureId(draft.lakeFeatureId);
            segment.setZoneId(draft.zoneId);
            segment.setFishingTargetId(draft.fishingTargetId);
            segment.setSegmentType(draft.type);
            segment.setStartedAt(draft.start);
            segment.setEndedAt(draft.end);
            segment.setDurationSeconds((int) Math.max(0, Duration.between(draft.start, draft.end).getSeconds()));
            segment.setRepresentativeLocation(draft.points.isEmpty() ? null : draft.points.get(draft.points.size() - 1));
            segment.setTrackGeometry(geoMapper.toLineString(draft.points));
            segment.setConfidence(BigDecimal.valueOf(draft.confidence));
            segment.setDerivationVersion(version);
            out.add(segment);
        }
        return out;
    }

    @SafeVarargs
    private static Instant firstNonNull(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record Draft(
            UUID sessionId,
            UUID tripWaypointId,
            UUID lakeFeatureId,
            UUID zoneId,
            UUID fishingTargetId,
            EffortSegmentType type,
            Instant start,
            Instant end,
            List<Point> points,
            double confidence
    ) {
        Draft merge(Draft other) {
            List<Point> combined = new ArrayList<>(points);
            combined.addAll(other.points);
            return new Draft(
                    sessionId,
                    tripWaypointId,
                    lakeFeatureId,
                    zoneId,
                    fishingTargetId,
                    type,
                    start,
                    other.end,
                    combined,
                    Math.max(confidence, other.confidence)
            );
        }
    }
}
