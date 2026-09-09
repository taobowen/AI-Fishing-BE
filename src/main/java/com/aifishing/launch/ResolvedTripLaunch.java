package com.aifishing.launch;

import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.enums.LaunchVerification;
import com.aifishing.common.enums.ShorelineKind;
import com.aifishing.planning.ranking.LaunchRecommendationScoreBreakdown;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

public record ResolvedTripLaunch(
        LaunchSelectionMode selectionMode,
        Point displayPoint,
        Point shoreAccessPoint,
        Point routeStartPoint,
        UUID officialAccessPointId,
        String officialName,
        String officialSource,
        LaunchVerification verification,
        String accessType,
        CustomAccessType customAccessType,
        ShorelineKind shorelineKind,
        Double snapDistanceMeters,
        String resolutionVersion,
        List<String> warnings,
        LaunchRecommendationScoreBreakdown autoScore
) {
    public ResolvedTripLaunch {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean known() {
        return routeStartPoint != null;
    }

    public static ResolvedTripLaunch unknownShore() {
        return new ResolvedTripLaunch(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ShorelineKind.UNKNOWN,
                null,
                null,
                List.of(),
                null
        );
    }
}
