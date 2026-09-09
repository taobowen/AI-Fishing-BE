package com.aifishing.planning.ranking;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyWeightResolverTest {

    private final StrategyWeightResolver resolver = new StrategyWeightResolver();

    @Test
    void windowPrefsWinAndGlobalsFillOmittedTypesWithoutAveraging() {
        FishingStrategyProfile profile = StrategyFixtures.validProfile();
        StrategyTimeWindow window = profile.timeWindows().get(0);

        List<StructurePreference> effective = resolver.effectiveStructures(window, profile);

        assertThat(effective).anySatisfy(pref -> {
            assertThat(pref.type()).isEqualTo(FeatureType.HUMP);
            assertThat(pref.weight()).isEqualTo(0.85);
        });
        double humpWindow = 0.85;
        double humpGlobal = 0.9;
        assertThat(humpWindow).isNotEqualTo((humpWindow + humpGlobal) / 2.0);
    }

    @Test
    void emptyWindowUsesGlobals() {
        FishingStrategyProfile profile = StrategyFixtures.validProfile();
        StrategyTimeWindow empty = new StrategyTimeWindow(
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                profile.timeWindows().get(0).preferredDepthM(),
                List.of(),
                List.of()
        );
        List<StructurePreference> effective = resolver.effectiveStructures(empty, profile);
        assertThat(effective).anySatisfy(pref -> {
            assertThat(pref.type()).isEqualTo(FeatureType.HUMP);
            assertThat(pref.weight()).isEqualTo(0.9);
        });
    }

    @Test
    void queryTypesHonorsWeightFloor() {
        List<StructurePreference> prefs = List.of(
                new StructurePreference(FeatureType.HUMP, 0.2, "ok"),
                new StructurePreference(FeatureType.FLAT, 0.04, "low")
        );
        assertThat(resolver.queryTypes(prefs, 0.05)).containsExactly(FeatureType.HUMP);
    }
}
