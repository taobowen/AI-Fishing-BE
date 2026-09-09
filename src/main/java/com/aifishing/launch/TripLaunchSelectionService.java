package com.aifishing.launch;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.launch.api.TripLaunchSelectionRequest;
import com.aifishing.launch.api.TripLaunchSelectionResponse;
import com.aifishing.launch.domain.TripLaunchSelection;
import com.aifishing.launch.repo.TripLaunchSelectionRepository;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class TripLaunchSelectionService {

    private final TripLaunchSelectionRepository selectionRepository;
    private final LakeAccessPointRepository accessPointRepository;
    private final LakePlanningGeometryLoader geometryLoader;
    private final CustomLaunchResolver customLaunchResolver;
    private final GeoMapper geoMapper;

    public TripLaunchSelectionService(
            TripLaunchSelectionRepository selectionRepository,
            LakeAccessPointRepository accessPointRepository,
            LakePlanningGeometryLoader geometryLoader,
            CustomLaunchResolver customLaunchResolver,
            GeoMapper geoMapper
    ) {
        this.selectionRepository = selectionRepository;
        this.accessPointRepository = accessPointRepository;
        this.geometryLoader = geometryLoader;
        this.customLaunchResolver = customLaunchResolver;
        this.geoMapper = geoMapper;
    }

    public void apply(UUID tripId, FishingMode mode, Lake lake, TripLaunchSelectionRequest request) {
        if (mode != FishingMode.BOAT) {
            selectionRepository.deleteById(tripId);
            return;
        }
        LaunchSelectionMode selectionMode = request == null || request.mode() == null
                ? LaunchSelectionMode.AUTO_RECOMMENDED
                : request.mode();
        TripLaunchSelection row = selectionRepository.findById(tripId).orElseGet(TripLaunchSelection::new);
        row.setTripId(tripId);
        clearCustom(row);
        row.setOfficialAccessPointId(null);
        row.setMode(selectionMode);
        switch (selectionMode) {
            case AUTO_RECOMMENDED -> {
                row.setWarnings(List.of());
            }
            case OFFICIAL_SELECTED -> {
                LakeAccessPoint point = requireOfficial(lake.getId(), request == null ? null : request.officialAccessPointId());
                row.setOfficialAccessPointId(point.getId());
                row.setWarnings(List.of());
            }
            case CUSTOM_SELECTED -> persistCustom(row, lake, request);
        }
        selectionRepository.save(row);
    }

    public void revalidateForLake(UUID tripId, FishingMode mode, Lake lake) {
        if (mode != FishingMode.BOAT) {
            selectionRepository.deleteById(tripId);
            return;
        }
        TripLaunchSelection row = selectionRepository.findById(tripId).orElse(null);
        if (row == null || row.getMode() == null || row.getMode() == LaunchSelectionMode.AUTO_RECOMMENDED) {
            return;
        }
        if (row.getMode() == LaunchSelectionMode.OFFICIAL_SELECTED) {
            requireOfficial(lake.getId(), row.getOfficialAccessPointId());
            return;
        }
        if (row.getRequestedPoint() == null) {
            throw new BadRequestException("CUSTOM_LAUNCH_ROUTE_ANCHOR_UNAVAILABLE", "Custom launch is missing a requested point");
        }
        persistResolvedCustom(row, lake, row.getRequestedPoint(), row.getCustomAccessType());
        selectionRepository.save(row);
    }

    public TripLaunchSelectionResponse toResponse(UUID tripId, FishingMode mode) {
        if (mode != FishingMode.BOAT) {
            return null;
        }
        TripLaunchSelection row = selectionRepository.findById(tripId).orElse(null);
        LaunchSelectionMode selectionMode = row == null || row.getMode() == null
                ? LaunchSelectionMode.AUTO_RECOMMENDED
                : row.getMode();
        LakeAccessPoint official = null;
        if (row != null && row.getOfficialAccessPointId() != null) {
            official = accessPointRepository.findById(row.getOfficialAccessPointId()).orElse(null);
        }
        return new TripLaunchSelectionResponse(
                selectionMode,
                official == null ? (row == null ? null : row.getOfficialAccessPointId()) : official.getId(),
                official == null ? null : official.getName(),
                official == null ? null : official.getSource(),
                selectionMode == LaunchSelectionMode.CUSTOM_SELECTED
                        ? LaunchVerification.USER_UNVERIFIED
                        : (selectionMode == LaunchSelectionMode.OFFICIAL_SELECTED ? LaunchVerification.AUTHORITATIVE : null),
                row == null ? null : geoMapper.toDto(row.getRequestedPoint()),
                row == null ? null : geoMapper.toDto(row.getShoreAccessPoint()),
                row == null ? null : geoMapper.toDto(row.getRouteStartPoint()),
                row == null || row.getSnapDistanceM() == null ? null : row.getSnapDistanceM().doubleValue(),
                row == null ? null : row.getCustomAccessType(),
                row == null ? null : row.getShorelineKind(),
                row == null ? null : row.getResolutionVersion(),
                row == null ? List.of() : row.getWarnings()
        );
    }

    public TripLaunchSelection require(UUID tripId) {
        return selectionRepository.findById(tripId).orElse(null);
    }

    private void persistCustom(TripLaunchSelection row, Lake lake, TripLaunchSelectionRequest request) {
        if (request == null || request.requestedPoint() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "requestedPoint is required for CUSTOM_SELECTED");
        }
        persistResolvedCustom(
                row,
                lake,
                geoMapper.toPoint(request.requestedPoint()),
                request.customAccessType() == null ? CustomAccessType.UNKNOWN : request.customAccessType()
        );
    }

    private void persistResolvedCustom(
            TripLaunchSelection row,
            Lake lake,
            Point requested,
            CustomAccessType accessType
    ) {
        LakePlanningGeometry geometry = geometryLoader.load(lake);
        CustomLaunchResolver.Resolution resolution = customLaunchResolver.resolve(requested, geometry)
                .orElseThrow(() -> previewFailure(requested, geometry));
        row.setRequestedPoint(requested);
        row.setShoreAccessPoint(resolution.shoreAccessPoint());
        row.setRouteStartPoint(resolution.routeStartPoint());
        row.setSnapDistanceM(BigDecimal.valueOf(resolution.snapDistanceMeters()).setScale(2, RoundingMode.HALF_UP));
        row.setCustomAccessType(accessType == null ? CustomAccessType.UNKNOWN : accessType);
        row.setShorelineKind(resolution.shorelineKind());
        row.setResolutionVersion(resolution.resolutionVersion());
        row.setWarnings(resolution.warnings());
    }

    private LakeAccessPoint requireOfficial(UUID lakeId, UUID officialId) {
        if (officialId == null) {
            throw new BadRequestException("VALIDATION_ERROR", "officialAccessPointId is required for OFFICIAL_SELECTED");
        }
        LakeAccessPoint point = accessPointRepository.findById(officialId)
                .orElseThrow(() -> new BadRequestException("VALIDATION_ERROR", "Official boat launch not found"));
        if (!lakeId.equals(point.getLakeId()) || !Boolean.TRUE.equals(point.getBoatLaunch())) {
            throw new BadRequestException("VALIDATION_ERROR", "Official boat launch does not belong to this lake");
        }
        return point;
    }

    private BadRequestException previewFailure(Point requested, LakePlanningGeometry geometry) {
        CustomLaunchResolver.FailureCode code = customLaunchResolver.failureCode(requested, geometry);
        if (code == CustomLaunchResolver.FailureCode.TOO_FAR) {
            return new BadRequestException(
                    CustomLaunchResolver.TOO_FAR, "Requested point is too far from this lake's shoreline");
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

    private static void clearCustom(TripLaunchSelection row) {
        row.setRequestedPoint(null);
        row.setShoreAccessPoint(null);
        row.setRouteStartPoint(null);
        row.setSnapDistanceM(null);
        row.setCustomAccessType(null);
        row.setShorelineKind(null);
        row.setResolutionVersion(null);
        row.setWarnings(List.of());
    }
}
