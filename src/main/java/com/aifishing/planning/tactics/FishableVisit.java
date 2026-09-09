package com.aifishing.planning.tactics;

import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.spatial.TargetKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishableVisit(
        UUID visitId,
        TargetKind targetKind,
        FeatureType featureType,
        Double representativeDepthM,
        Double minDepthM,
        Double maxDepthM,
        List<TechniqueType> techniques,
        Instant arrivalAt,
        Instant departureAt,
        int fishMinutes,
        String structureHint,
        boolean pathLike
) {
    public FishableVisit {
        techniques = techniques == null ? List.of() : List.copyOf(techniques);
    }
}
