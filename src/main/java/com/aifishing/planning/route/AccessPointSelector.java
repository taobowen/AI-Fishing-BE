package com.aifishing.planning.route;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.trip.domain.Trip;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class AccessPointSelector {

    private final LakeAccessPointRepository accessPointRepository;

    public AccessPointSelector(LakeAccessPointRepository accessPointRepository) {
        this.accessPointRepository = accessPointRepository;
    }

    public AccessResolution resolve(Trip trip, UUID requestedAccessPointId) {
        List<LakeAccessPoint> points = accessPointRepository.findByLakeId(trip.getLakeId());
        if (requestedAccessPointId != null) {
            LakeAccessPoint match = points.stream()
                    .filter(point -> requestedAccessPointId.equals(point.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Access point does not belong to this lake"));
            if (!suitable(match, trip.getFishingMode())) {
                throw new BadRequestException("Access point is not suitable for " + trip.getFishingMode());
            }
            return new AccessResolution(
                    AccessResolution.AccessStatus.SELECTED,
                    match.getId(),
                    match.getName(),
                    match.getLocation()
            );
        }
        return points.stream()
                .filter(point -> suitable(point, trip.getFishingMode()))
                .min(Comparator.comparing(point -> point.getName() == null ? "" : point.getName()))
                .map(point -> new AccessResolution(
                        AccessResolution.AccessStatus.AUTHORITATIVE,
                        point.getId(),
                        point.getName(),
                        point.getLocation()
                ))
                .orElseGet(AccessResolution::unknown);
    }

    private static boolean suitable(LakeAccessPoint point, FishingMode mode) {
        if (point.getLocation() == null) {
            return false;
        }
        if (mode == FishingMode.BOAT) {
            return Boolean.TRUE.equals(point.getBoatLaunch());
        }
        return Boolean.TRUE.equals(point.getShoreAccess());
    }
}
