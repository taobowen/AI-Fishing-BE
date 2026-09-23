package com.aifishing.fishingsession.service;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.domain.WaypointSkipReason;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WaypointProgressMachine {

    private final SessionProperties properties;
    private final TripStopSubtargetRepository subtargetRepository;

    public WaypointProgressMachine(SessionProperties properties, TripStopSubtargetRepository subtargetRepository) {
        this.properties = properties;
        this.subtargetRepository = subtargetRepository;
    }

    public void applyAcceptedHistory(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            List<SessionLocationPoint> acceptedUpToCurrent
    ) {
        applyAcceptedHistory(progress, waypoints, acceptedUpToCurrent, null);
    }

    public void applyAcceptedHistory(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            List<SessionLocationPoint> acceptedUpToCurrent,
            UUID activeGuidanceTargetTripWaypointId
    ) {
        if (acceptedUpToCurrent.isEmpty()) {
            return;
        }
        SessionWaypointProgress current = executionWaypoint(progress, activeGuidanceTargetTripWaypointId);
        if (current == null) {
            return;
        }
        TripWaypoint planned = waypoints.get(current.getTripWaypointId());
        if (planned == null || planned.getLocation() == null) {
            return;
        }
        SessionLocationPoint point = acceptedUpToCurrent.getLast();
        SessionLocationPoint previous = acceptedUpToCurrent.size() < 2
                ? null
                : acceptedUpToCurrent.get(acceptedUpToCurrent.size() - 2);
        Point there = arrivalPoint(planned);
        if (there == null) {
            return;
        }
        double distanceM = planned.getFishingCorridor() != null && !planned.getFishingCorridor().isEmpty()
                ? GeoMetrics.distanceM(point.getLocation(), planned.getFishingCorridor())
                : GeoMetrics.distanceM(point.getLocation(), there);
        recordClosest(current, distanceM);

        SessionProperties.Waypoint cfg = properties.getWaypoint();
        if (distanceM <= cfg.getApproachRadiusM() && current.getFirstApproachedAt() == null) {
            current.setFirstApproachedAt(point.getRecordedAt());
        }

        boolean guidanceTarget = activeGuidanceTargetTripWaypointId != null
                && activeGuidanceTargetTripWaypointId.equals(current.getTripWaypointId());
        switch (current.getStatus()) {
            case NAVIGATING -> maybeArrive(current, acceptedUpToCurrent, there, distanceM, cfg);
            case UPCOMING -> {
                if (guidanceTarget) {
                    maybeArrive(current, acceptedUpToCurrent, there, distanceM, cfg);
                }
            }
            case ARRIVED, FISHING -> maybeDwellAndDepart(current, progress, previous, point, there, distanceM, cfg);
            default -> {
            }
        }
    }

    static SessionWaypointProgress executionWaypoint(
            List<SessionWaypointProgress> progress,
            UUID activeGuidanceTargetTripWaypointId
    ) {
        SessionWaypointProgress targeted = SessionMapper.progressForWaypoint(progress, activeGuidanceTargetTripWaypointId);
        if (targeted != null
                && targeted.getStatus() != WaypointProgressStatus.SKIPPED
                && targeted.getStatus() != WaypointProgressStatus.COMPLETED) {
            return targeted;
        }
        return SessionMapper.currentWaypoint(progress);
    }

    public void manualArrive(SessionWaypointProgress row, Instant at) {
        if (row.getStatus() == WaypointProgressStatus.SKIPPED
                || row.getStatus() == WaypointProgressStatus.COMPLETED) {
            return;
        }
        if (row.getStatus() == WaypointProgressStatus.ARRIVED
                || row.getStatus() == WaypointProgressStatus.FISHING) {
            return;
        }
        enterFishing(row, at);
    }

    public void manualSkip(SessionWaypointProgress row, Instant at, List<SessionWaypointProgress> all) {
        skip(row, at, all, WaypointSkipReason.USER);
    }

    public void skipLateStart(SessionWaypointProgress row, Instant at) {
        if (row.getStatus() == WaypointProgressStatus.SKIPPED
                || row.getStatus() == WaypointProgressStatus.COMPLETED) {
            return;
        }
        row.setStatus(WaypointProgressStatus.SKIPPED);
        row.setSkipReason(WaypointSkipReason.LATE_START);
        row.setSkippedAt(at);
    }

    public void skip(
            SessionWaypointProgress row,
            Instant at,
            List<SessionWaypointProgress> all,
            WaypointSkipReason reason
    ) {
        if (row.getStatus() == WaypointProgressStatus.SKIPPED
                || row.getStatus() == WaypointProgressStatus.COMPLETED) {
            return;
        }
        boolean wasCurrent = isActive(row);
        row.setStatus(WaypointProgressStatus.SKIPPED);
        row.setSkipReason(reason == null ? WaypointSkipReason.USER : reason);
        row.setSkippedAt(at);
        if (wasCurrent) {
            promoteNext(all, row.getSequence());
        }
    }

    public void manualComplete(SessionWaypointProgress row, Instant at, List<SessionWaypointProgress> all) {
        if (row.getStatus() == WaypointProgressStatus.SKIPPED
                || row.getStatus() == WaypointProgressStatus.COMPLETED) {
            return;
        }
        boolean wasCurrent = isActive(row);
        row.setStatus(WaypointProgressStatus.COMPLETED);
        if (row.getCompletedAt() == null) {
            row.setCompletedAt(at);
        }
        if (row.getArrivedAt() == null) {
            row.setArrivedAt(at);
        }
        if (wasCurrent) {
            promoteNext(all, row.getSequence());
        }
    }

    public void finalizeInProgress(List<SessionWaypointProgress> all, Instant at) {
        SessionWaypointProgress current = SessionMapper.currentWaypoint(all);
        if (current == null) {
            return;
        }
        current.setStatus(WaypointProgressStatus.COMPLETED);
        if (current.getCompletedAt() == null) {
            current.setCompletedAt(at);
        }
        if (current.getArrivedAt() == null) {
            current.setArrivedAt(at);
        }
    }

    public static boolean arrivalConfirmed(
            List<SessionLocationPoint> consecutiveInside,
            int confirmSeconds,
            int confirmSamples
    ) {
        if (consecutiveInside.size() < confirmSamples) {
            return false;
        }
        Instant first = consecutiveInside.getFirst().getRecordedAt();
        Instant last = consecutiveInside.getLast().getRecordedAt();
        return Duration.between(first, last).getSeconds() >= confirmSeconds;
    }

    Point arrivalPoint(TripWaypoint planned) {
        TargetKind kind = planned.resolvedTargetKind();
        if (kind.isPathLike()) {
            Point pathStart = lineStart(planned.getSelectedFishingPath());
            if (pathStart != null) {
                return pathStart;
            }
            return planned.resolvedEntryPoint();
        }
        if (kind.isZoneVisit()) {
            Point child = firstChildEntry(planned.getId());
            if (child != null) {
                return child;
            }
        }
        return planned.resolvedEntryPoint();
    }

    private Point firstChildEntry(UUID waypointId) {
        if (subtargetRepository == null || waypointId == null) {
            return null;
        }
        List<TripStopSubtarget> children = subtargetRepository.findByTripWaypointIdOrderBySequenceAsc(waypointId);
        for (TripStopSubtarget child : children) {
            if (child.getEntryPoint() != null && !child.getEntryPoint().isEmpty()) {
                return child.getEntryPoint();
            }
        }
        return null;
    }

    private static Point lineStart(LineString path) {
        if (path == null || path.isEmpty() || path.getNumPoints() < 1) {
            return null;
        }
        return path.getStartPoint();
    }

    static List<SessionLocationPoint> trailingInside(
            List<SessionLocationPoint> acceptedUpToCurrent,
            Point waypoint,
            double arrivalRadiusM
    ) {
        List<SessionLocationPoint> window = new ArrayList<>();
        for (int i = acceptedUpToCurrent.size() - 1; i >= 0; i--) {
            SessionLocationPoint candidate = acceptedUpToCurrent.get(i);
            if (GeoMetrics.distanceM(candidate.getLocation(), waypoint) > arrivalRadiusM) {
                break;
            }
            window.addFirst(candidate);
        }
        return window;
    }

    private void maybeArrive(
            SessionWaypointProgress current,
            List<SessionLocationPoint> acceptedUpToCurrent,
            Point waypoint,
            double distanceM,
            SessionProperties.Waypoint cfg
    ) {
        if (distanceM > cfg.getArrivalRadiusM()) {
            return;
        }
        List<SessionLocationPoint> window = trailingInside(acceptedUpToCurrent, waypoint, cfg.getArrivalRadiusM());
        if (!arrivalConfirmed(window, cfg.getArrivalConfirmSeconds(), cfg.getArrivalConfirmSamples())) {
            return;
        }
        Instant at = acceptedUpToCurrent.getLast().getRecordedAt();
        current.setStatus(WaypointProgressStatus.ARRIVED);
        current.setArrivedAt(at);
        current.setStatus(WaypointProgressStatus.FISHING);
    }

    private void maybeDwellAndDepart(
            SessionWaypointProgress current,
            List<SessionWaypointProgress> progress,
            SessionLocationPoint previousAccepted,
            SessionLocationPoint point,
            Point waypoint,
            double distanceM,
            SessionProperties.Waypoint cfg
    ) {
        boolean insideDeparture = distanceM <= cfg.getDepartureRadiusM();
        boolean previousInside = previousAccepted != null
                && GeoMetrics.distanceM(previousAccepted.getLocation(), waypoint) <= cfg.getDepartureRadiusM();
        if (previousInside && insideDeparture) {
            int elapsed = (int) Math.max(
                    0,
                    Duration.between(previousAccepted.getRecordedAt(), point.getRecordedAt()).getSeconds()
            );
            current.setAccumulatedDwellSeconds(current.getAccumulatedDwellSeconds() + elapsed);
        }
        if (current.getStatus() == WaypointProgressStatus.ARRIVED) {
            current.setStatus(WaypointProgressStatus.FISHING);
        }
        if (current.getStatus() == WaypointProgressStatus.FISHING && !insideDeparture) {
            current.setDepartedAt(point.getRecordedAt());
            current.setCompletedAt(point.getRecordedAt());
            current.setStatus(WaypointProgressStatus.COMPLETED);
            promoteNext(progress, current.getSequence());
        }
    }

    private void promoteNext(List<SessionWaypointProgress> all, int afterSequence) {
        if (SessionMapper.currentWaypoint(all) != null) {
            return;
        }
        all.stream()
                .filter(row -> row.getSequence() > afterSequence)
                .filter(row -> row.getStatus() == WaypointProgressStatus.UPCOMING)
                .min(Comparator.comparingInt(SessionWaypointProgress::getSequence))
                .ifPresent(next -> next.setStatus(WaypointProgressStatus.NAVIGATING));
    }

    private static boolean isActive(SessionWaypointProgress row) {
        WaypointProgressStatus status = row.getStatus();
        return status == WaypointProgressStatus.NAVIGATING
                || status == WaypointProgressStatus.ARRIVED
                || status == WaypointProgressStatus.FISHING;
    }

    private static void enterFishing(SessionWaypointProgress row, Instant at) {
        row.setStatus(WaypointProgressStatus.FISHING);
        if (row.getArrivedAt() == null) {
            row.setArrivedAt(at);
        }
        if (row.getFirstApproachedAt() == null) {
            row.setFirstApproachedAt(at);
        }
    }

    private static void recordClosest(SessionWaypointProgress current, double distanceM) {
        BigDecimal value = BigDecimal.valueOf(distanceM).setScale(2, RoundingMode.HALF_UP);
        if (current.getClosestDistanceM() == null || value.compareTo(current.getClosestDistanceM()) < 0) {
            current.setClosestDistanceM(value);
        }
    }
}
