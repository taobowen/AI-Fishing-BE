package com.aifishing.strategy.domain;

import java.util.List;

public record FishingStrategyProfile(
        List<TargetSpeciesPriority> targetSpecies,
        List<StructurePreference> structurePreferences,
        List<StrategyTimeWindow> timeWindows,
        List<TechniquePreference> generalTechniques,
        WeatherInterpretation weatherInterpretation,
        Double modelConfidence,
        Double systemConfidence,
        List<String> warnings,
        List<DataLimitation> dataLimitations
) {
    public FishingStrategyProfile {
        targetSpecies = targetSpecies == null ? List.of() : List.copyOf(targetSpecies);
        structurePreferences = structurePreferences == null ? List.of() : List.copyOf(structurePreferences);
        timeWindows = timeWindows == null ? List.of() : List.copyOf(timeWindows);
        generalTechniques = generalTechniques == null ? List.of() : List.copyOf(generalTechniques);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        dataLimitations = dataLimitations == null ? List.of() : List.copyOf(dataLimitations);
    }

    public FishingStrategyProfile withBackendFields(
            double systemConfidence,
            List<DataLimitation> limitations,
            List<String> extraWarnings
    ) {
        java.util.LinkedHashSet<String> mergedWarnings = new java.util.LinkedHashSet<>(warnings);
        if (extraWarnings != null) {
            mergedWarnings.addAll(extraWarnings);
        }
        java.util.LinkedHashMap<DataLimitationCode, DataLimitation> merged = new java.util.LinkedHashMap<>();
        for (DataLimitation limitation : dataLimitations) {
            if (limitation != null && limitation.code() != null) {
                merged.putIfAbsent(limitation.code(), limitation);
            }
        }
        if (limitations != null) {
            for (DataLimitation limitation : limitations) {
                if (limitation != null && limitation.code() != null) {
                    merged.put(limitation.code(), limitation);
                }
            }
        }
        return new FishingStrategyProfile(
                targetSpecies,
                structurePreferences,
                timeWindows,
                generalTechniques,
                weatherInterpretation,
                modelConfidence,
                systemConfidence,
                List.copyOf(mergedWarnings),
                List.copyOf(merged.values())
        );
    }
}
