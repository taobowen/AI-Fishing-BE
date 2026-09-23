package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.lake.processing.extract.GeoMetrics;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-side "Fishing here?" suggestion. Stationary is not FISHING.
 */
@Component
public class StationaryFishingDetector {

    static final String DISMISSED_AT = "stationaryPromptDismissedAt";
    static final String DISMISSED_LAT = "stationaryPromptLat";
    static final String DISMISSED_LNG = "stationaryPromptLng";

    private final SessionProperties properties;

    public StationaryFishingDetector(SessionProperties properties) {
        this.properties = properties;
    }

    public boolean shouldPrompt(Input input) {
        if (input == null || input.now() == null) {
            return false;
        }
        if (input.status() != FishingSessionStatus.ACTIVE || input.paused() || input.adHocOpen()) {
            return false;
        }
        if (input.currentProgress() != WaypointProgressStatus.NAVIGATING) {
            return false;
        }
        SessionProperties.Waypoint waypoint = properties.getWaypoint();
        if (input.distanceToPlannedArrivalM() != null
                && input.distanceToPlannedArrivalM() <= waypoint.getArrivalRadiusM()) {
            return false;
        }
        SessionProperties.Stationary cfg = properties.getStationary();
        SessionProperties.Location locationCfg = properties.getLocation();
        List<SessionLocationPoint> ordered = orderedAccepted(input.recentAccepted());
        if (ordered.size() < cfg.getMinSamples()) {
            return false;
        }
        SessionLocationPoint last = ordered.getLast();
        if (!freshUsable(last, input.now(), cfg.getGpsMaxAgeSeconds(), locationCfg.getMaxAccuracyM())) {
            return false;
        }
        List<SessionLocationPoint> window = trailingSlowStable(ordered, last, cfg);
        if (window.size() < cfg.getMinSamples()) {
            return false;
        }
        long duration = Duration.between(window.getFirst().getRecordedAt(), window.getLast().getRecordedAt())
                .getSeconds();
        if (duration < cfg.getMinDurationSeconds()) {
            return false;
        }
        if (centroidSpreadM(window) > cfg.getStableRadiusM()) {
            return false;
        }
        if (stillSuppressed(input, last, cfg)) {
            return false;
        }
        return true;
    }

    static boolean freshUsable(
            SessionLocationPoint point,
            Instant at,
            int maxAgeSeconds,
            double maxAccuracyM
    ) {
        if (point == null || point.getLocation() == null || at == null || point.getRecordedAt() == null) {
            return false;
        }
        if (point.getQuality() != null && point.getQuality() != LocationQuality.ACCEPTED) {
            return false;
        }
        long age = Math.abs(Duration.between(point.getRecordedAt(), at).getSeconds());
        if (age > maxAgeSeconds) {
            return false;
        }
        if (point.getAccuracyM() != null && point.getAccuracyM().doubleValue() > maxAccuracyM) {
            return false;
        }
        return true;
    }

    private static List<SessionLocationPoint> orderedAccepted(List<SessionLocationPoint> recent) {
        if (recent == null || recent.isEmpty()) {
            return List.of();
        }
        return recent.stream()
                .filter(point -> point != null && point.getLocation() != null && point.getRecordedAt() != null)
                .filter(point -> point.getQuality() == null || point.getQuality() == LocationQuality.ACCEPTED)
                .sorted(Comparator.comparing(SessionLocationPoint::getRecordedAt)
                        .thenComparing(point -> point.getClientPointId() == null ? "" : point.getClientPointId()))
                .toList();
    }

    private static List<SessionLocationPoint> trailingSlowStable(
            List<SessionLocationPoint> ordered,
            SessionLocationPoint last,
            SessionProperties.Stationary cfg
    ) {
        List<SessionLocationPoint> window = new ArrayList<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            SessionLocationPoint candidate = ordered.get(i);
            if (speedMps(ordered, i) > cfg.getMaxSpeedMps()) {
                break;
            }
            if (GeoMetrics.distanceM(candidate.getLocation(), last.getLocation()) > cfg.getStableRadiusM()) {
                break;
            }
            window.addFirst(candidate);
        }
        return window;
    }

    private static double speedMps(List<SessionLocationPoint> ordered, int index) {
        SessionLocationPoint point = ordered.get(index);
        if (point.getSpeedMps() != null) {
            return point.getSpeedMps().doubleValue();
        }
        if (index == 0) {
            return 0;
        }
        SessionLocationPoint previous = ordered.get(index - 1);
        long seconds = Math.max(1, Duration.between(previous.getRecordedAt(), point.getRecordedAt()).getSeconds());
        return GeoMetrics.distanceM(previous.getLocation(), point.getLocation()) / seconds;
    }

    private static double centroidSpreadM(List<SessionLocationPoint> window) {
        double lat = 0;
        double lng = 0;
        for (SessionLocationPoint point : window) {
            lat += point.getLocation().getY();
            lng += point.getLocation().getX();
        }
        double n = window.size();
        Point last = window.getLast().getLocation();
        double max = 0;
        for (SessionLocationPoint point : window) {
            max = Math.max(max, GeoMetrics.distanceM(point.getLocation(), last));
            double fromCentroidLat = (point.getLocation().getY() - lat / n) * GeoMetrics.metersPerDegreeLat();
            double fromCentroidLng = (point.getLocation().getX() - lng / n)
                    * GeoMetrics.metersPerDegreeLng(lat / n);
            max = Math.max(max, Math.hypot(fromCentroidLat, fromCentroidLng));
        }
        return max;
    }

    private static boolean stillSuppressed(Input input, SessionLocationPoint last, SessionProperties.Stationary cfg) {
        if (input.dismissedAt() == null) {
            return false;
        }
        if (Duration.between(input.dismissedAt(), input.now()).getSeconds() >= cfg.getSuppressCooldownSeconds()) {
            return false;
        }
        if (input.dismissedLocation() != null && last.getLocation() != null
                && GeoMetrics.distanceM(input.dismissedLocation(), last.getLocation()) > cfg.getSuppressMoveM()) {
            return false;
        }
        return true;
    }

    public record Input(
            FishingSessionStatus status,
            boolean paused,
            boolean adHocOpen,
            WaypointProgressStatus currentProgress,
            Double distanceToPlannedArrivalM,
            List<SessionLocationPoint> recentAccepted,
            Instant now,
            Instant dismissedAt,
            Point dismissedLocation
    ) {
    }
}
