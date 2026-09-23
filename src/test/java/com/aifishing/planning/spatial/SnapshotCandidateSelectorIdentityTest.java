package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateCompressionReason;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.ranking.StrategyWeightResolver;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotCandidateSelectorIdentityTest {

    @AfterEach
    void clear() {
        GenerateProfiler.clear();
    }

    @Test
    void copyWithStrategyDoesNotMutateSourceAndCollapseKeepsOnePhysicalIdentity() {
        CandidateSpot source = new CandidateSpot();
        source.setFishingTargetId(UUID.nameUUIDFromBytes("identity".getBytes()));
        source.setType(FeatureType.HUMP);
        source.setRepresentativeDepthM(3.0);
        source.setFeatureConfidence(0.8);
        source.setLocation(RoutePlannerHarness.point(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT));
        double originalWeight = source.getStrategyWeight();

        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 4, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        SnapshotCandidateSelector selector = new SnapshotCandidateSelector(new StrategyWeightResolver());
        GenerateProfiler.begin();
        var window = new StrategyTimeWindow(
                LocalTime.of(6, 0),
                LocalTime.of(10, 0),
                new DepthRange(2, 4),
                List.of(new StructurePreference(FeatureType.HUMP, 0.85, "morning")),
                List.of()
        );
        CandidateSpot morning = invokeCopy(selector, source, window, context);
        CandidateSpot afternoon = invokeCopy(selector, source, new StrategyTimeWindow(
                LocalTime.of(12, 0),
                LocalTime.of(16, 0),
                new DepthRange(2, 4),
                List.of(new StructurePreference(FeatureType.HUMP, 0.4, "afternoon")),
                List.of()
        ), context);

        assertThat(source.getStrategyWeight()).isEqualTo(originalWeight);
        assertThat(morning).isNotSameAs(source);
        assertThat(afternoon).isNotSameAs(source);
        assertThat(morning.getStrategyWeight()).isEqualTo(0.85);
        assertThat(afternoon.getStrategyWeight()).isEqualTo(0.4);

        EnumMap<RejectionReason, Integer> rejections = new EnumMap<>(RejectionReason.class);
        assertThat(rejections).doesNotContainKey(RejectionReason.DUPLICATE);
        GenerateProfiler.current().compression().add(CandidateCompressionReason.TIME_VARIANT_MERGED, 1);
        assertThat(GenerateProfiler.current().compression().count(CandidateCompressionReason.TIME_VARIANT_MERGED))
                .isEqualTo(1);
    }

    @Test
    void collapseKeepsFirstWindowFallbackWeightNotMaxAcrossWindows() {
        CandidateSpot morning = new CandidateSpot();
        UUID id = UUID.nameUUIDFromBytes("identity-collapse".getBytes());
        morning.setFishingTargetId(id);
        morning.setType(FeatureType.HUMP);
        morning.setStrategyWeight(0.4);
        morning.setStrategyRationale("morning");
        CandidateSpot afternoon = morning.copy();
        afternoon.setStrategyWeight(0.85);
        afternoon.setStrategyRationale("afternoon");
        GenerateProfiler.begin();
        SnapshotCandidateSelector selector = new SnapshotCandidateSelector(new StrategyWeightResolver());
        @SuppressWarnings("unchecked")
        List<CandidateSpot> collapsed = invokeCollapse(selector, List.of(morning, afternoon));
        assertThat(collapsed).hasSize(1);
        assertThat(collapsed.get(0).getStrategyWeight()).isEqualTo(0.4);
        assertThat(collapsed.get(0).getWindowFrom()).isNull();
        assertThat(GenerateProfiler.current().compression().count(CandidateCompressionReason.TIME_VARIANT_MERGED))
                .isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static List<CandidateSpot> invokeCollapse(SnapshotCandidateSelector selector, List<CandidateSpot> selected) {
        try {
            var method = SnapshotCandidateSelector.class.getDeclaredMethod("collapseToPhysicalIdentity", List.class);
            method.setAccessible(true);
            return (List<CandidateSpot>) method.invoke(selector, selected);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static CandidateSpot invokeCopy(
            SnapshotCandidateSelector selector,
            CandidateSpot source,
            StrategyTimeWindow window,
            PlanningContext context
    ) {
        try {
            var method = SnapshotCandidateSelector.class.getDeclaredMethod(
                    "copyWithStrategy",
                    CandidateSpot.class,
                    StrategyTimeWindow.class,
                    List.class,
                    List.class,
                    boolean.class
            );
            method.setAccessible(true);
            StrategyWeightResolver resolver = new StrategyWeightResolver();
            return (CandidateSpot) method.invoke(
                    selector,
                    source,
                    window,
                    resolver.effectiveStructures(window, context.profile()),
                    resolver.effectiveTechniques(window, context.profile()),
                    true
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
