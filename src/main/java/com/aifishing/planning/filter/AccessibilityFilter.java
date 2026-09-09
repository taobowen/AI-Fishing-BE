package com.aifishing.planning.filter;

import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.ingestion.repo.LakeAccessPointRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.service.PlanningContext;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccessibilityFilter implements CandidateFilter {

    private static final double SHORE_MAX_DISTANCE_M = 80;

    private final LakeAccessPointRepository accessPointRepository;
    private final LakeWaterwayRepository waterwayRepository;

    public AccessibilityFilter(
            LakeAccessPointRepository accessPointRepository,
            LakeWaterwayRepository waterwayRepository
    ) {
        this.accessPointRepository = accessPointRepository;
        this.waterwayRepository = waterwayRepository;
    }

    @Override
    public FilterResult apply(CandidateSpot candidate, PlanningContext context) {
        Point point = candidate.getLocation();
        if (point == null || point.isEmpty()) {
            return FilterResult.reject(RejectionReason.INVALID_GEOMETRY);
        }
        if (!context.geometry().hasWater() || !context.geometry().inWater(point)) {
            return FilterResult.reject(RejectionReason.OUTSIDE_LAKE);
        }
        if (context.geometry().onIsland(point)) {
            return FilterResult.reject(RejectionReason.ON_ISLAND);
        }
        if (context.fishingMode() == FishingMode.SHORE) {
            return applyShore(candidate, context, point);
        }
        return FilterResult.accept();
    }

    private FilterResult applyShore(CandidateSpot candidate, PlanningContext context, Point point) {
        List<Geometry> shorelines = waterwayRepository.findByLakeIdAndType(context.lake().getId(), "SHORELINE")
                .stream()
                .map(waterway -> waterway.getGeometry())
                .filter(geometry -> geometry != null && !geometry.isEmpty())
                .toList();
        boolean nearShore = shorelines.isEmpty()
                || shorelines.stream().anyMatch(shore -> GeoMetrics.distanceM(point, shore) <= SHORE_MAX_DISTANCE_M);
        if (!nearShore) {
            return FilterResult.reject(RejectionReason.SHORE_INACCESSIBLE);
        }
        boolean verified = accessPointRepository.findByLakeId(context.lake().getId()).stream()
                .anyMatch(access -> Boolean.TRUE.equals(access.getShoreAccess()) && access.getLocation() != null);
        if (!verified) {
            candidate.setShoreAccessUnverified(true);
            candidate.addWarning("SHORE_ACCESS_UNVERIFIED");
            return FilterResult.accept("SHORE_ACCESS_UNVERIFIED");
        }
        return FilterResult.accept();
    }
}
