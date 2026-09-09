package com.aifishing.strategy.domain;

import java.time.LocalTime;
import java.util.List;

public record StrategyTimeWindow(
        LocalTime from,
        LocalTime to,
        DepthRange preferredDepthM,
        List<StructurePreference> structurePreferences,
        List<TechniquePreference> techniques,
        LightPreference lightPreference
) {
    public StrategyTimeWindow {
        structurePreferences = structurePreferences == null ? List.of() : List.copyOf(structurePreferences);
        techniques = techniques == null ? List.of() : List.copyOf(techniques);
        lightPreference = lightPreference == null ? LightPreference.NEUTRAL : lightPreference;
    }

    public StrategyTimeWindow(
            LocalTime from,
            LocalTime to,
            DepthRange preferredDepthM,
            List<StructurePreference> structurePreferences,
            List<TechniquePreference> techniques
    ) {
        this(from, to, preferredDepthM, structurePreferences, techniques, LightPreference.NEUTRAL);
    }
}
