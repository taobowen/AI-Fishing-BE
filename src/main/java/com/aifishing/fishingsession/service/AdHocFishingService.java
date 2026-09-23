package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.dto.ClientEventRequest;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.contracts.EventSource;
import com.aifishing.guidance.events.ActivityStateUpdater;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.LakeFishingZoneMemberRepository;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One start path for Fish Here and "Start Fishing". Does not call the LLM.
 */
@Service
public class AdHocFishingService {

    private static final Logger log = LoggerFactory.getLogger(AdHocFishingService.class);

    private final SessionProperties sessionProperties;
    private final SessionAdHocFishingStopRepository stopRepository;
    private final SessionLocationPointRepository locationPointRepository;
    private final LakeFishingTargetRepository fishingTargetRepository;
    private final LakeFishingZoneMemberRepository zoneMemberRepository;
    private final ActivityStateUpdater activityStateUpdater;
    private final SessionEventWriter sessionEventWriter;

    public AdHocFishingService(
            SessionProperties sessionProperties,
            SessionAdHocFishingStopRepository stopRepository,
            SessionLocationPointRepository locationPointRepository,
            LakeFishingTargetRepository fishingTargetRepository,
            LakeFishingZoneMemberRepository zoneMemberRepository,
            ActivityStateUpdater activityStateUpdater,
            SessionEventWriter sessionEventWriter
    ) {
        this.sessionProperties = sessionProperties;
        this.stopRepository = stopRepository;
        this.locationPointRepository = locationPointRepository;
        this.fishingTargetRepository = fishingTargetRepository;
        this.zoneMemberRepository = zoneMemberRepository;
        this.activityStateUpdater = activityStateUpdater;
        this.sessionEventWriter = sessionEventWriter;
    }

    public Optional<SessionAdHocFishingStop> findOpen(UUID sessionId) {
        return stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(sessionId);
    }

    public boolean hasOpenStop(UUID sessionId) {
        return findOpen(sessionId).isPresent();
    }

    @Transactional
    public SessionAdHocFishingStop start(FishingSession session, ClientEventRequest request) {
        requireStartable(session);
        Optional<SessionAdHocFishingStop> byClient = stopRepository
                .findByFishingSessionIdAndClientEventId(session.getId(), request.clientEventId());
        if (byClient.isPresent()) {
            SessionAdHocFishingStop existing = byClient.get();
            if (existing.isOpen()) {
                refreshAndRouteStart(session, request, existing);
            }
            return existing;
        }
        Optional<SessionAdHocFishingStop> open = findOpen(session.getId());
        if (open.isPresent()) {
            return open.get();
        }
        SessionLocationPoint gps = requireFreshGps(session, request.occurredAt());
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setId(UUID.randomUUID());
        stop.setFishingSessionId(session.getId());
        stop.setClientEventId(request.clientEventId());
        stop.setStartedAt(request.occurredAt());
        stop.setLocation(gps.getLocation());
        stop.setGpsAccuracyM(gps.getAccuracyM());
        applyNearbyMatch(stop, gps.getLocation());
        try {
            stopRepository.saveAndFlush(stop);
        } catch (DataIntegrityViolationException ex) {
            SessionAdHocFishingStop raced = stopRepository
                    .findByFishingSessionIdAndClientEventId(session.getId(), request.clientEventId())
                    .or(() -> findOpen(session.getId()))
                    .orElseThrow(() -> ex);
            if (raced.isOpen()) {
                refreshAndRouteStart(session, request, raced);
            }
            return raced;
        }
        refreshAndRouteStart(session, request, stop);
        return stop;
    }

    @Transactional
    public Optional<SessionAdHocFishingStop> end(FishingSession session, ClientEventRequest request) {
        Optional<SessionAdHocFishingStop> byClient = stopRepository
                .findByFishingSessionIdAndClientEventId(session.getId(), request.clientEventId());
        if (byClient.isPresent() && !byClient.get().isOpen()) {
            return byClient;
        }
        SessionAdHocFishingStop open = findOpen(session.getId()).orElse(null);
        if (open == null) {
            return byClient;
        }
        return Optional.of(endOpen(session, open, request.occurredAt(), request.clientEventId(), EventSource.CLIENT));
    }

    @Transactional
    public void maybeEndFromDeparture(FishingSession session, List<SessionLocationPoint> acceptedUpToCurrent) {
        SessionAdHocFishingStop open = findOpen(session.getId()).orElse(null);
        if (open == null || open.getLocation() == null || acceptedUpToCurrent == null || acceptedUpToCurrent.isEmpty()) {
            return;
        }
        SessionProperties.AdHoc cfg = sessionProperties.getAdHoc();
        List<SessionLocationPoint> outside = trailingOutside(
                acceptedUpToCurrent,
                open.getLocation(),
                cfg.getDepartureRadiusM()
        );
        if (!departureConfirmed(outside, cfg.getDepartureConfirmSeconds(), cfg.getDepartureConfirmSamples())) {
            return;
        }
        Instant at = acceptedUpToCurrent.getLast().getRecordedAt();
        endOpen(session, open, at, "ad-hoc-end:" + open.getId(), EventSource.SERVER);
    }

    @Transactional
    public void closeQuietly(FishingSession session, Instant endedAt) {
        findOpen(session.getId()).ifPresent(open -> {
            open.setEndedAt(endedAt);
            activityStateUpdater.refresh(session, endedAt);
            sessionEventWriter.auditAdHocEnded(
                    session,
                    endedAt,
                    "ad-hoc-quiet-end:" + open.getId(),
                    payload(session, open)
            );
        });
    }

    @Transactional
    public void dismissStationaryPrompt(FishingSession session, Instant at, SessionLocationPoint lastAccepted) {
        Map<String, Object> summary = session.getSummary() == null
                ? new HashMap<>()
                : new HashMap<>(session.getSummary());
        summary.put(StationaryFishingDetector.DISMISSED_AT, at.toString());
        if (lastAccepted != null && lastAccepted.getLocation() != null) {
            summary.put(StationaryFishingDetector.DISMISSED_LAT, lastAccepted.getLocation().getY());
            summary.put(StationaryFishingDetector.DISMISSED_LNG, lastAccepted.getLocation().getX());
        }
        session.setSummary(summary);
    }

    private void refreshAndRouteStart(
            FishingSession session,
            ClientEventRequest request,
            SessionAdHocFishingStop stop
    ) {
        activityStateUpdater.refresh(session, request.occurredAt());
        sessionEventWriter.onAdHocStarted(session, request, payload(session, stop));
    }

    private SessionAdHocFishingStop endOpen(
            FishingSession session,
            SessionAdHocFishingStop open,
            Instant endedAt,
            String idempotencyKey,
            EventSource source
    ) {
        open.setEndedAt(endedAt);
        activityStateUpdater.refresh(session, endedAt);
        sessionEventWriter.onAdHocEnded(session, endedAt, idempotencyKey, source, payload(session, open));
        return open;
    }

    private static Map<String, Object> payload(FishingSession session, SessionAdHocFishingStop stop) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("adHocFishingStopId", stop.getId().toString());
        if (stop.getLocation() != null) {
            payload.put("latitudeWgs84", stop.getLocation().getY());
            payload.put("longitudeWgs84", stop.getLocation().getX());
        }
        if (stop.getFishingTargetId() != null) {
            payload.put("fishingTargetId", stop.getFishingTargetId().toString());
        }
        if (stop.getZoneId() != null) {
            payload.put("physicalZoneId", stop.getZoneId().toString());
        }
        if (stop.getLakeFeatureId() != null) {
            payload.put("lakeFeatureId", stop.getLakeFeatureId().toString());
        }
        return payload;
    }

    private static void requireStartable(FishingSession session) {
        if (session.getStatus() == null || session.getStatus().isTerminal()) {
            throw new BadRequestException("Session is already " + session.getStatus());
        }
        if (session.getStatus() == FishingSessionStatus.PAUSED) {
            throw new BadRequestException("Cannot start ad-hoc fishing while paused");
        }
        if (session.getStatus() != FishingSessionStatus.ACTIVE) {
            throw new BadRequestException("Session must be ACTIVE");
        }
    }

    private SessionLocationPoint requireFreshGps(FishingSession session, Instant at) {
        SessionLocationPoint last = locationPointRepository
                .findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(
                        session.getId(), LocationQuality.ACCEPTED)
                .orElseThrow(() -> new BadRequestException("Fresh usable GPS is required"));
        SessionProperties.AdHoc adHoc = sessionProperties.getAdHoc();
        if (!StationaryFishingDetector.freshUsable(
                last,
                at,
                adHoc.getGpsMaxAgeSeconds(),
                sessionProperties.getLocation().getMaxAccuracyM()
        )) {
            throw new BadRequestException("Fresh usable GPS is required");
        }
        return last;
    }

    private void applyNearbyMatch(SessionAdHocFishingStop stop, Point location) {
        if (location == null) {
            return;
        }
        try {
            int radius = sessionProperties.getAdHoc().getMatchRadiusM();
            List<LakeFishingTarget> nearby = fishingTargetRepository.findNearby(
                    location.getY(), location.getX(), radius);
            LakeFishingTarget best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LakeFishingTarget target : nearby) {
                if (target.getRepresentativePoint() == null) {
                    continue;
                }
                double distance = GeoMetrics.distanceM(location, target.getRepresentativePoint());
                if (distance <= radius && distance < bestDistance) {
                    best = target;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                return;
            }
            stop.setFishingTargetId(best.getId());
            if (best.getSourceFeatureIds() != null && !best.getSourceFeatureIds().isEmpty()) {
                stop.setLakeFeatureId(best.getSourceFeatureIds().getFirst());
            }
            List<LakeFishingZoneMember> members = zoneMemberRepository.findByFishingTargetIdOrderBySequenceAsc(best.getId());
            if (!members.isEmpty()) {
                stop.setZoneId(members.getFirst().getZoneId());
            }
        } catch (RuntimeException ex) {
            log.info("Ad-hoc GIS match failed open: {}", ex.getMessage());
        }
    }

    static List<SessionLocationPoint> trailingOutside(
            List<SessionLocationPoint> acceptedUpToCurrent,
            Point origin,
            double departureRadiusM
    ) {
        List<SessionLocationPoint> window = new ArrayList<>();
        for (int i = acceptedUpToCurrent.size() - 1; i >= 0; i--) {
            SessionLocationPoint candidate = acceptedUpToCurrent.get(i);
            if (candidate.getLocation() == null
                    || GeoMetrics.distanceM(candidate.getLocation(), origin) <= departureRadiusM) {
                break;
            }
            window.addFirst(candidate);
        }
        return window;
    }

    static boolean departureConfirmed(
            List<SessionLocationPoint> consecutiveOutside,
            int confirmSeconds,
            int confirmSamples
    ) {
        if (consecutiveOutside.size() < confirmSamples) {
            return false;
        }
        Instant first = consecutiveOutside.getFirst().getRecordedAt();
        Instant last = consecutiveOutside.getLast().getRecordedAt();
        return Duration.between(first, last).getSeconds() >= confirmSeconds;
    }

    static Instant parseInstant(Object raw) {
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    static Double parseDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
