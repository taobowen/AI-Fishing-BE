package com.aifishing.planning.service;

import com.aifishing.common.enums.CandidateSource;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.spatial.TargetKind;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripWaypointLakeFeatureTest {

    @Test
    void syntheticRequiredPointIsNotStoredAsLakeFeature() {
        UUID synthetic = UUID.nameUUIDFromBytes("req-point-1".getBytes());
        CandidateSpot spot = new CandidateSpot();
        spot.setCandidateSource(CandidateSource.REQUIRED);
        spot.setTargetKind(TargetKind.POINT);
        spot.setFeatureId(synthetic);
        spot.setFishingTargetId(synthetic);

        assertThat(TripPlanningService.rawLakeFeatureId(spot)).isEqualTo(synthetic);
        assertThat(TripPlanningService.lakeFeatureIdForPersist(synthetic, Set.of())).isNull();
    }

    @Test
    void realLakeFeatureIdIsKept() {
        UUID featureId = UUID.randomUUID();
        CandidateSpot spot = new CandidateSpot();
        spot.setCandidateSource(CandidateSource.AI);
        spot.setTargetKind(TargetKind.POINT);
        spot.setFeatureId(featureId);

        assertThat(TripPlanningService.lakeFeatureIdForPersist(
                TripPlanningService.rawLakeFeatureId(spot), Set.of(featureId))).isEqualTo(featureId);
    }
}
