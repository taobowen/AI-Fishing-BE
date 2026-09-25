package com.aifishing.trip.service;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.trip.api.RequiredPointReachabilityResponse;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RequiredPointReachabilityEstimatorTest {

    private final GeoMapper geoMapper = new GeoMapper();
    private final PlanningProperties properties = new PlanningProperties();
    private final GeoPointDto launch = new GeoPointDto(44.75, -78.92);
    private final Point launchPoint = geoMapper.toPoint(launch);

    @Test
    void shoreModeSkipsRangeCap() {
        RequiredPointReachabilityResponse response = RequiredPointReachabilityEstimator.estimate(
                FishingMode.SHORE,
                launch,
                launchPoint,
                kayak(),
                LocalTime.of(6, 0),
                LocalTime.of(15, 0),
                properties
        );
        assertThat(response.applyRangeCap()).isFalse();
        assertThat(response.oneWayCapKm()).isNull();
        assertThat(response.routeStart()).isEqualTo(launch);
        assertThat(response.disclaimer()).isEqualTo(RequiredPointReachabilityEstimator.DISCLAIMER);
    }

    @Test
    void unknownLaunchSkipsRangeCap() {
        RequiredPointReachabilityResponse response = RequiredPointReachabilityEstimator.estimate(
                FishingMode.BOAT,
                null,
                null,
                kayak(),
                LocalTime.of(6, 0),
                LocalTime.of(15, 0),
                properties
        );
        assertThat(response.applyRangeCap()).isFalse();
        assertThat(response.oneWayCapKm()).isNull();
    }

    @Test
    void unknownBoatSkipsRangeCap() {
        RequiredPointReachabilityResponse response = RequiredPointReachabilityEstimator.estimate(
                FishingMode.BOAT,
                launch,
                launchPoint,
                null,
                LocalTime.of(6, 0),
                LocalTime.of(15, 0),
                properties
        );
        assertThat(response.applyRangeCap()).isFalse();
        assertThat(response.oneWayCapKm()).isNull();
    }

    @Test
    void boatCapMatchesBoatCapabilityFilterTravelCapKm() {
        Boat boat = fishingBoat();
        LocalTime start = LocalTime.of(6, 0);
        LocalTime end = LocalTime.of(15, 0);
        RequiredPointReachabilityResponse response = RequiredPointReachabilityEstimator.estimate(
                FishingMode.BOAT,
                launch,
                launchPoint,
                boat,
                start,
                end,
                properties
        );
        double expected = BoatCapabilityFilter.travelCapKm(context(boat, start, end));
        // 12 km/h * 9 h * 0.25 = 27; FISHING_BOAT max one-way = 20 → 20
        assertThat(expected).isCloseTo(20.0, within(1e-9));
        assertThat(response.applyRangeCap()).isTrue();
        assertThat(response.oneWayCapKm()).isCloseTo(expected, within(1e-9));
        assertThat(response.routeStart()).isEqualTo(launch);
    }

    @Test
    void shortWindowUsesDurationCap() {
        Boat boat = fishingBoat();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(9, 0);
        RequiredPointReachabilityResponse response = RequiredPointReachabilityEstimator.estimate(
                FishingMode.BOAT,
                launch,
                launchPoint,
                boat,
                start,
                end,
                properties
        );
        // 12 km/h * 1 h * 0.25 = 3; FISHING_BOAT max = 20 → 3
        assertThat(response.oneWayCapKm()).isCloseTo(3.0, within(1e-9));
        assertThat(response.applyRangeCap()).isTrue();
    }

    private PlanningContext context(Boat boat, LocalTime start, LocalTime end) {
        Trip trip = new Trip();
        trip.setId(UUID.randomUUID());
        trip.setUserId(UUID.randomUUID());
        trip.setLakeId(UUID.randomUUID());
        trip.setFishingMode(FishingMode.BOAT);
        trip.setFishingStartTime(start);
        trip.setFishingEndTime(end);
        return new PlanningContext(
                trip,
                null,
                boat,
                new AccessResolution(AccessResolution.AccessStatus.SELECTED, null, null, launchPoint),
                null,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                properties,
                new ArrayList<>()
        );
    }

    private static Boat kayak() {
        Boat boat = new Boat();
        boat.setType(BoatType.KAYAK);
        return boat;
    }

    private static Boat fishingBoat() {
        Boat boat = new Boat();
        boat.setType(BoatType.FISHING_BOAT);
        return boat;
    }
}
