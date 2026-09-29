package com.aifishing.planning.tactics;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.spatial.TargetKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdealTacticHeuristicTest {

    private final IdealTacticHeuristic heuristic = new IdealTacticHeuristic();

    @Test
    void shallowWaterCanBeTopwaterWhileDeeperStructureIsNot() {
        assertThat(IdealTacticHeuristic.idealFamily(visit(FeatureType.POINT, TargetKind.POINT, 0.8, false)))
                .isEqualTo(LureFamily.TOPWATER);
        assertThat(IdealTacticHeuristic.idealFamily(visit(FeatureType.HUMP, TargetKind.POINT, 8.0, false)))
                .isEqualTo(LureFamily.DROP_SHOT_BAIT);
        assertThat(IdealTacticHeuristic.idealFamily(visit(FeatureType.FLAT, TargetKind.POINT, 3.0, false)))
                .isEqualTo(LureFamily.SPINNERBAIT);
        assertThat(IdealTacticHeuristic.idealFamily(visit(FeatureType.POINT, TargetKind.POINT, null, false)))
                .isEqualTo(LureFamily.JERKBAIT);
        assertThat(heuristic.profileFor(visit(FeatureType.DROP_OFF, TargetKind.POINT, 5.0, false), null).idealTactic().lureFamily())
                .isEqualTo(LureFamily.JIG);
    }

    private static FishableVisit visit(FeatureType type, TargetKind kind, Double depthM, boolean pathLike) {
        return new FishableVisit(
                UUID.randomUUID(),
                kind,
                type,
                depthM,
                depthM,
                depthM,
                List.of(TechniqueType.TOPWATER),
                null,
                null,
                20,
                null,
                pathLike
        );
    }
}
