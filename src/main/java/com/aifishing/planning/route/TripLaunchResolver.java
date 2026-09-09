package com.aifishing.planning.route;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.ingestion.domain.AccessOwnership;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.launch.CustomLaunchResolver;
import com.aifishing.launch.LaunchProperties;
import com.aifishing.launch.ResolvedTripLaunch;
import com.aifishing.launch.TripLaunchSelectionService;
import com.aifishing.launch.domain.TripLaunchSelection;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.trip.domain.Trip;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TripLaunchResolver {

    public static final String LAUNCH_SELECTION_CONFLICT = "LAUNCH_SELECTION_CONFLICT";
    public static final String CUSTOM_LAUNCH_MOVED = "CUSTOM_LAUNCH_MOVED";
    public static final String OFFICIAL_UNROUTABLE = "OFFICIAL_LAUNCH_UNROUTABLE";

    private final AccessPointSelector accessPointSelector;
    private final TripLaunchSelectionService selectionService;
    private final LakeAccessPointRepository accessPointRepository;
    private final CustomLaunchResolver customLaunchResolver;
    private final LaunchRecommender launchRecommender;
    private final LaunchProperties launchProperties;

    public TripLaunchResolver(
            AccessPointSelector accessPointSelector,
            TripLaunchSelectionService selectionService,
            LakeAccessPointRepository accessPointRepository,
            CustomLaunchResolver customLaunchResolver,
            LaunchRecommender launchRecommender,
            LaunchProperties launchProperties
    ) {
        this.accessPointSelector = accessPointSelector;
        this.selectionService = selectionService;
        this.accessPointRepository = accessPointRepository;
        this.customLaunchResolver = customLaunchResolver;
        this.launchRecommender = launchRecommender;
        this.launchProperties = launchProperties;
    }

    public AccessResolution shoreAccess(Trip trip, UUID requestedAccessPointId) {
        return accessPointSelector.resolve(trip, requestedAccessPointId);
    }

    public Result resolveBoat(
            Trip trip,
            UUID requestAccessPointId,
            LakePlanningGeometry geometry,
            PlanningContext contextForRecommend,
            List<CandidateSpot> candidates
    ) {
        TripLaunchSelection stored = selectionService.require(trip.getId());
        LaunchSelectionMode mode = stored == null || stored.getMode() == null
                ? LaunchSelectionMode.AUTO_RECOMMENDED
                : stored.getMode();
        if (requestAccessPointId != null && mode != LaunchSelectionMode.AUTO_RECOMMENDED) {
            boolean conflict = mode == LaunchSelectionMode.CUSTOM_SELECTED
                    || !requestAccessPointId.equals(stored.getOfficialAccessPointId());
            if (conflict) {
                throw new BadRequestException(
                        LAUNCH_SELECTION_CONFLICT,
                        "Generate Plan accessPointId conflicts with the trip launch selection");
            }
        }
        if (mode == LaunchSelectionMode.AUTO_RECOMMENDED && requestAccessPointId != null) {
            return official(trip.getLakeId(), requestAccessPointId, geometry, LaunchSelectionMode.OFFICIAL_SELECTED, true);
        }
        return switch (mode) {
            case AUTO_RECOMMENDED -> {
                LaunchRecommender.Result recommended = launchRecommender.recommend(contextForRecommend, candidates);
                if (recommended.failed()) {
                    yield Result.failed(recommended.errorCode());
                }
                yield Result.ok(recommended.launch());
            }
            case OFFICIAL_SELECTED -> official(
                    trip.getLakeId(),
                    stored.getOfficialAccessPointId(),
                    geometry,
                    LaunchSelectionMode.OFFICIAL_SELECTED,
                    false
            );
            case CUSTOM_SELECTED -> custom(stored, geometry);
        };
    }

    public AccessResolution toAccessResolution(ResolvedTripLaunch launch) {
        if (launch == null || !launch.known()) {
            return AccessResolution.unknown();
        }
        AccessResolution.AccessStatus status = launch.selectionMode() == LaunchSelectionMode.OFFICIAL_SELECTED
                || launch.selectionMode() == LaunchSelectionMode.AUTO_RECOMMENDED
                ? (launch.officialAccessPointId() != null
                ? AccessResolution.AccessStatus.AUTHORITATIVE
                : AccessResolution.AccessStatus.UNKNOWN)
                : AccessResolution.AccessStatus.SELECTED;
        if (launch.selectionMode() == LaunchSelectionMode.OFFICIAL_SELECTED) {
            status = AccessResolution.AccessStatus.SELECTED;
        }
        if (launch.selectionMode() == LaunchSelectionMode.CUSTOM_SELECTED) {
            status = AccessResolution.AccessStatus.SELECTED;
        }
        return new AccessResolution(
                status,
                launch.officialAccessPointId(),
                launch.officialName(),
                launch.routeStartPoint()
        );
    }

    public ResolvedTripLaunch fromShore(AccessResolution access) {
        if (access == null || !access.known()) {
            return ResolvedTripLaunch.unknownShore();
        }
        return new ResolvedTripLaunch(
                null,
                access.location(),
                access.location(),
                access.location(),
                access.accessPointId(),
                access.name(),
                null,
                LaunchVerification.AUTHORITATIVE,
                null,
                null,
                ShorelineKind.UNKNOWN,
                null,
                null,
                List.of(),
                null
        );
    }

    private Result official(
            UUID lakeId,
            UUID officialId,
            LakePlanningGeometry geometry,
            LaunchSelectionMode mode,
            boolean runScopedOverride
    ) {
        if (officialId == null) {
            return Result.failed(LaunchRecommender.NO_KNOWN);
        }
        LakeAccessPoint point = accessPointRepository.findById(officialId).orElse(null);
        if (point == null || !lakeId.equals(point.getLakeId()) || !Boolean.TRUE.equals(point.getBoatLaunch())) {
            throw new BadRequestException("VALIDATION_ERROR", "Access point is not a boat launch on this lake");
        }
        CustomLaunchResolver.Resolution resolution = customLaunchResolver.resolve(point.getLocation(), geometry)
                .orElse(null);
        if (resolution == null) {
            return Result.failed(OFFICIAL_UNROUTABLE);
        }
        return Result.ok(new ResolvedTripLaunch(
                runScopedOverride ? LaunchSelectionMode.OFFICIAL_SELECTED : mode,
                point.getLocation(),
                resolution.shoreAccessPoint(),
                resolution.routeStartPoint(),
                point.getId(),
                point.getName(),
                point.getSource(),
                LaunchVerification.AUTHORITATIVE,
                CustomLaunchResolver.accessType(point),
                null,
                resolution.shorelineKind(),
                resolution.snapDistanceMeters(),
                resolution.resolutionVersion(),
                AccessOwnership.launchWarnings(point.getOwnershipType(), resolution.warnings()),
                null
        ));
    }

    private Result custom(TripLaunchSelection stored, LakePlanningGeometry geometry) {
        if (stored.getRequestedPoint() == null) {
            return Result.failed(CustomLaunchResolver.ANCHOR_UNAVAILABLE);
        }
        boolean versionMatch = launchProperties.getCustom().getResolutionVersion()
                .equals(stored.getResolutionVersion());
        boolean valid = versionMatch
                && customLaunchResolver.routeStartValid(stored.getRouteStartPoint(), geometry);
        if (valid) {
            return Result.ok(fromStoredCustom(stored));
        }
        CustomLaunchResolver.Resolution resolution = customLaunchResolver
                .resolve(stored.getRequestedPoint(), geometry)
                .orElse(null);
        if (resolution == null) {
            return Result.failed(CustomLaunchResolver.ANCHOR_UNAVAILABLE);
        }
        if (stored.getRouteStartPoint() != null
                && customLaunchResolver.materialMove(stored.getRouteStartPoint(), resolution.routeStartPoint())) {
            return Result.failed(CUSTOM_LAUNCH_MOVED);
        }
        if (stored.getShoreAccessPoint() != null
                && customLaunchResolver.materialMove(stored.getShoreAccessPoint(), resolution.shoreAccessPoint())) {
            return Result.failed(CUSTOM_LAUNCH_MOVED);
        }
        return Result.ok(new ResolvedTripLaunch(
                LaunchSelectionMode.CUSTOM_SELECTED,
                stored.getRequestedPoint(),
                resolution.shoreAccessPoint(),
                resolution.routeStartPoint(),
                null,
                null,
                null,
                LaunchVerification.USER_UNVERIFIED,
                stored.getCustomAccessType() == null ? CustomAccessType.UNKNOWN.name() : stored.getCustomAccessType().name(),
                stored.getCustomAccessType(),
                resolution.shorelineKind(),
                resolution.snapDistanceMeters(),
                resolution.resolutionVersion(),
                resolution.warnings(),
                null
        ));
    }

    private ResolvedTripLaunch fromStoredCustom(TripLaunchSelection stored) {
        return new ResolvedTripLaunch(
                LaunchSelectionMode.CUSTOM_SELECTED,
                stored.getRequestedPoint(),
                stored.getShoreAccessPoint(),
                stored.getRouteStartPoint(),
                null,
                null,
                null,
                LaunchVerification.USER_UNVERIFIED,
                stored.getCustomAccessType() == null ? CustomAccessType.UNKNOWN.name() : stored.getCustomAccessType().name(),
                stored.getCustomAccessType(),
                stored.getShorelineKind(),
                stored.getSnapDistanceM() == null ? null : stored.getSnapDistanceM().doubleValue(),
                stored.getResolutionVersion(),
                stored.getWarnings(),
                null
        );
    }

    public record Result(ResolvedTripLaunch launch, String errorCode) {
        static Result ok(ResolvedTripLaunch launch) {
            return new Result(launch, null);
        }

        static Result failed(String code) {
            return new Result(null, code);
        }

        public boolean failed() {
            return errorCode != null;
        }
    }
}
