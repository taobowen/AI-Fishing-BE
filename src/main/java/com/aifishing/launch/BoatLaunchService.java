package com.aifishing.launch;

import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.launch.api.BoatLaunchResponse;
import com.aifishing.launch.api.CustomLaunchPreviewRequest;
import com.aifishing.launch.api.CustomLaunchPreviewResponse;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BoatLaunchService {

    private final LakeRepository lakeRepository;
    private final LakeAccessPointRepository accessPointRepository;
    private final LakePlanningGeometryLoader geometryLoader;
    private final CustomLaunchResolver customLaunchResolver;
    private final GeoMapper geoMapper;

    public BoatLaunchService(
            LakeRepository lakeRepository,
            LakeAccessPointRepository accessPointRepository,
            LakePlanningGeometryLoader geometryLoader,
            CustomLaunchResolver customLaunchResolver,
            GeoMapper geoMapper
    ) {
        this.lakeRepository = lakeRepository;
        this.accessPointRepository = accessPointRepository;
        this.geometryLoader = geometryLoader;
        this.customLaunchResolver = customLaunchResolver;
        this.geoMapper = geoMapper;
    }

    /**
     * Known launches come from lake ingest ({@code lake_access_points}), not a live water-snap.
     * Route-start snapping still happens at generate / custom preview.
     */
    @Transactional(readOnly = true)
    public List<BoatLaunchResponse> list(UUID lakeId) {
        if (!lakeRepository.existsById(lakeId)) {
            throw new NotFoundException("Lake not found");
        }
        return knownBoatLaunches(lakeId).stream()
                .sorted(Comparator.comparing(point -> point.getName() == null ? "" : point.getName()))
                .map(this::toListResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomLaunchPreviewResponse preview(UUID lakeId, CustomLaunchPreviewRequest request) {
        Lake lake = lakeRepository.findById(lakeId)
                .orElseThrow(() -> new NotFoundException("Lake not found"));
        Point requested = geoMapper.toPoint(request.requestedPoint());
        LakePlanningGeometry geometry = geometryLoader.load(lake);
        CustomLaunchResolver.Resolution resolution = customLaunchResolver.resolve(requested, geometry)
                .orElseThrow(() -> failure(customLaunchResolver.failureCode(requested, geometry)));
        return new CustomLaunchPreviewResponse(
                geoMapper.toDto(resolution.requestedPoint()),
                geoMapper.toDto(resolution.shoreAccessPoint()),
                geoMapper.toDto(resolution.routeStartPoint()),
                round(resolution.snapDistanceMeters()),
                resolution.shorelineKind(),
                request.customAccessType(),
                resolution.resolutionVersion(),
                resolution.warnings(),
                customLaunchResolver.nearbyOfficial(requested, knownBoatLaunches(lakeId), geometry)
        );
    }

    public List<LakeAccessPoint> knownBoatLaunches(UUID lakeId) {
        return accessPointRepository.findByLakeId(lakeId).stream()
                .filter(point -> Boolean.TRUE.equals(point.getBoatLaunch()) && point.getLocation() != null)
                .toList();
    }

    public List<LakeAccessPoint> autoEligible(UUID lakeId) {
        return knownBoatLaunches(lakeId).stream()
                .filter(point -> AccessOwnership.autoAllowed(point.getOwnershipType()))
                .toList();
    }

    private static BadRequestException failure(CustomLaunchResolver.FailureCode code) {
        if (code == CustomLaunchResolver.FailureCode.TOO_FAR) {
            return new BadRequestException(
                    CustomLaunchResolver.TOO_FAR,
                    "Requested point is too far from this lake's shoreline");
        }
        if (code == CustomLaunchResolver.FailureCode.ANCHOR_UNAVAILABLE) {
            return new BadRequestException(
                    CustomLaunchResolver.ANCHOR_UNAVAILABLE,
                    "Could not place a water-side route start from this shoreline");
        }
        return new BadRequestException(
                CustomLaunchResolver.ANCHOR_UNAVAILABLE,
                "Lake water geometry is not available for a custom launch");
    }

    private BoatLaunchResponse toListResponse(LakeAccessPoint point) {
        return new BoatLaunchResponse(
                point.getId(),
                point.getName(),
                geoMapper.toDto(point.getLocation()),
                CustomLaunchResolver.accessType(point),
                point.getSource(),
                LaunchVerification.AUTHORITATIVE,
                true,
                point.getOwnershipType(),
                AccessOwnership.launchWarnings(point.getOwnershipType(), List.of())
        );
    }

    private static Double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
