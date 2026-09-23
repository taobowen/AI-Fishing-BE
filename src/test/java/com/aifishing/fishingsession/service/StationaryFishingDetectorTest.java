package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationaryFishingDetectorTest {

    private static final Instant NOW = Instant.parse("2026-09-17T16:00:00Z");
    private static final double LAT = 44.75;
    private static final double LNG = -79.35;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    private final StationaryFishingDetector detector = new StationaryFishingDetector(new SessionProperties());

    @Test
    void shortStopDoesNotPrompt() {
        assertThat(detector.shouldPrompt(input(points(3, 10, 0), null, null))).isFalse();
    }

    @Test
    void driftingOverLargeAreaDoesNotPrompt() {
        List<SessionLocationPoint> drifted = new ArrayList<>();
        Instant t0 = NOW.minusSeconds(60);
        for (int i = 0; i < 6; i++) {
            drifted.add(point("d" + i, t0.plusSeconds(i * 10), LAT + (i * 0.002), LNG, 0.1));
        }
        assertThat(detector.shouldPrompt(input(drifted, null, null))).isFalse();
    }

    @Test
    void stableStopLongEnoughPrompts() {
        assertThat(detector.shouldPrompt(input(points(6, 10, 0.1), null, null))).isTrue();
    }

    @Test
    void plannedArrivalDoesNotPrompt() {
        assertThat(detector.shouldPrompt(input(points(6, 10, 0.1), 20.0, null))).isFalse();
    }

    @Test
    void keepNavigatingSuppressesUntilCooldownOrMove() {
        SessionLocationPoint last = points(6, 10, 0.1).getLast();
        StationaryFishingDetector.Input suppressed = new StationaryFishingDetector.Input(
                FishingSessionStatus.ACTIVE,
                false,
                false,
                WaypointProgressStatus.NAVIGATING,
                400.0,
                points(6, 10, 0.1),
                NOW,
                NOW.minusSeconds(30),
                last.getLocation()
        );
        assertThat(detector.shouldPrompt(suppressed)).isFalse();
    }

    @Test
    void movementRearmsAfterDismiss() {
        Point dismissed = point(LAT, LNG);
        List<SessionLocationPoint> moved = pointsAt(6, 10, 0.1, LAT + 0.01, LNG);
        StationaryFishingDetector.Input rearmed = new StationaryFishingDetector.Input(
                FishingSessionStatus.ACTIVE,
                false,
                false,
                WaypointProgressStatus.NAVIGATING,
                400.0,
                moved,
                NOW,
                NOW.minusSeconds(30),
                dismissed
        );
        assertThat(detector.shouldPrompt(rearmed)).isTrue();
    }

    @Test
    void openAdHocStopDoesNotPrompt() {
        StationaryFishingDetector.Input adHoc = new StationaryFishingDetector.Input(
                FishingSessionStatus.ACTIVE,
                false,
                true,
                WaypointProgressStatus.NAVIGATING,
                400.0,
                points(6, 10, 0.1),
                NOW,
                null,
                null
        );
        assertThat(detector.shouldPrompt(adHoc)).isFalse();
    }

    @Test
    void pausedSessionDoesNotPrompt() {
        StationaryFishingDetector.Input paused = new StationaryFishingDetector.Input(
                FishingSessionStatus.ACTIVE,
                true,
                false,
                WaypointProgressStatus.NAVIGATING,
                400.0,
                points(6, 10, 0.1),
                NOW,
                null,
                null
        );
        assertThat(detector.shouldPrompt(paused)).isFalse();
    }

    @Test
    void staleGpsDoesNotPrompt() {
        List<SessionLocationPoint> stale = points(6, 10, 0.1);
        stale.forEach(point -> point.setRecordedAt(point.getRecordedAt().minusSeconds(120)));
        assertThat(detector.shouldPrompt(input(stale, null, null))).isFalse();
    }

    private StationaryFishingDetector.Input input(
            List<SessionLocationPoint> points,
            Double distanceToArrivalM,
            Instant dismissedAt
    ) {
        return new StationaryFishingDetector.Input(
                FishingSessionStatus.ACTIVE,
                false,
                false,
                WaypointProgressStatus.NAVIGATING,
                distanceToArrivalM == null ? 400.0 : distanceToArrivalM,
                points,
                NOW,
                dismissedAt,
                null
        );
    }

    private static List<SessionLocationPoint> points(int count, int gapSeconds, double speedMps) {
        return pointsAt(count, gapSeconds, speedMps, LAT, LNG);
    }

    private static List<SessionLocationPoint> pointsAt(
            int count,
            int gapSeconds,
            double speedMps,
            double lat,
            double lng
    ) {
        List<SessionLocationPoint> points = new ArrayList<>();
        Instant first = NOW.minusSeconds((long) (count - 1) * gapSeconds);
        for (int i = 0; i < count; i++) {
            points.add(point("p" + i, first.plusSeconds((long) i * gapSeconds), lat, lng, speedMps));
        }
        return points;
    }

    private static SessionLocationPoint point(
            String id,
            Instant recordedAt,
            double lat,
            double lng,
            double speedMps
    ) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setClientPointId(id);
        point.setRecordedAt(recordedAt);
        point.setLocation(point(lat, lng));
        point.setQuality(LocationQuality.ACCEPTED);
        point.setSpeedMps(BigDecimal.valueOf(speedMps));
        point.setAccuracyM(BigDecimal.valueOf(8));
        return point;
    }

    private static Point point(double lat, double lng) {
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }
}
