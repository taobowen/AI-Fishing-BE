package com.aifishing.trip.service;

import com.aifishing.auth.CurrentUser;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.trip.api.RequiredPointReachabilityRequest;
import com.aifishing.trip.api.RequiredPointReachabilityResponse;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequiredPointReachabilityService {

    private final BoatRepository boatRepository;
    private final GeoMapper geoMapper;
    private final PlanningProperties planningProperties;
    private final CurrentUser currentUser;

    public RequiredPointReachabilityService(
            BoatRepository boatRepository,
            GeoMapper geoMapper,
            PlanningProperties planningProperties,
            CurrentUser currentUser
    ) {
        this.boatRepository = boatRepository;
        this.geoMapper = geoMapper;
        this.planningProperties = planningProperties;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public RequiredPointReachabilityResponse estimate(RequiredPointReachabilityRequest request) {
        currentUser.id();
        FishingMode mode = request.fishingMode();
        GeoPointDto routeStartDto = request.routeStart();
        Point routeStartPoint = routeStartDto == null ? null : geoMapper.toPoint(routeStartDto);

        if (mode != FishingMode.BOAT) {
            return RequiredPointReachabilityEstimator.skip(routeStartDto);
        }
        if (routeStartPoint == null || request.boatId() == null) {
            return RequiredPointReachabilityEstimator.skip(routeStartDto);
        }

        Boat boat = boatRepository.findByIdAndUserIdAndActiveTrue(request.boatId(), currentUser.id())
                .orElseThrow(() -> new NotFoundException("Boat not found"));

        return RequiredPointReachabilityEstimator.estimate(
                mode,
                routeStartDto,
                routeStartPoint,
                boat,
                request.fishingStartTime(),
                request.fishingEndTime(),
                planningProperties
        );
    }
}
