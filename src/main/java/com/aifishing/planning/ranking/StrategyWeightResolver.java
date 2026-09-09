package com.aifishing.planning.ranking;

import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyTimeWindow;
import com.aifishing.strategy.domain.StructurePreference;
import com.aifishing.strategy.domain.TechniquePreference;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StrategyWeightResolver {

    public List<StructurePreference> effectiveStructures(StrategyTimeWindow window, FishingStrategyProfile profile) {
        List<StructurePreference> globals = profile == null ? List.of() : profile.structurePreferences();
        List<StructurePreference> windowPrefs = window == null ? List.of() : window.structurePreferences();
        return mergeByType(windowPrefs, globals);
    }

    public List<TechniquePreference> effectiveTechniques(StrategyTimeWindow window, FishingStrategyProfile profile) {
        List<TechniquePreference> globals = profile == null ? List.of() : profile.generalTechniques();
        List<TechniquePreference> windowPrefs = window == null ? List.of() : window.techniques();
        return mergeTechniques(windowPrefs, globals);
    }

    public List<FeatureType> queryTypes(List<StructurePreference> effective, double minWeight) {
        List<FeatureType> types = new ArrayList<>();
        for (StructurePreference preference : effective) {
            if (preference != null && preference.type() != null && preference.weight() > minWeight) {
                types.add(preference.type());
            }
        }
        return types;
    }

    public double weightFor(List<StructurePreference> effective, FeatureType type) {
        for (StructurePreference preference : effective) {
            if (preference.type() == type) {
                return preference.weight();
            }
        }
        return 0;
    }

    public String rationaleFor(List<StructurePreference> effective, FeatureType type) {
        for (StructurePreference preference : effective) {
            if (preference.type() == type) {
                return preference.rationale();
            }
        }
        return null;
    }

    private List<StructurePreference> mergeByType(
            List<StructurePreference> windowPrefs,
            List<StructurePreference> globals
    ) {
        Map<FeatureType, StructurePreference> byType = new LinkedHashMap<>();
        if (windowPrefs == null || windowPrefs.isEmpty()) {
            for (StructurePreference preference : globals) {
                if (preference != null && preference.type() != null) {
                    byType.put(preference.type(), preference);
                }
            }
        } else {
            for (StructurePreference preference : windowPrefs) {
                if (preference != null && preference.type() != null) {
                    byType.put(preference.type(), preference);
                }
            }
            for (StructurePreference preference : globals) {
                if (preference != null && preference.type() != null) {
                    byType.putIfAbsent(preference.type(), preference);
                }
            }
        }
        return List.copyOf(byType.values());
    }

    private List<TechniquePreference> mergeTechniques(
            List<TechniquePreference> windowPrefs,
            List<TechniquePreference> globals
    ) {
        Map<TechniqueType, TechniquePreference> byType = new LinkedHashMap<>();
        if (windowPrefs == null || windowPrefs.isEmpty()) {
            for (TechniquePreference preference : globals) {
                if (preference != null && preference.type() != null) {
                    byType.put(preference.type(), preference);
                }
            }
        } else {
            for (TechniquePreference preference : windowPrefs) {
                if (preference != null && preference.type() != null) {
                    byType.put(preference.type(), preference);
                }
            }
            for (TechniquePreference preference : globals) {
                if (preference != null && preference.type() != null) {
                    byType.putIfAbsent(preference.type(), preference);
                }
            }
        }
        return List.copyOf(byType.values());
    }
}
