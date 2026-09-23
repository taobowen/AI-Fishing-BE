package com.aifishing.guidance.contracts;

import java.util.List;

public record RetrievedMemory(
        UserFishingPreferences userPreferences,
        List<InferredUserPreference> inferredPreferences,
        List<String> retrievedMemoryIds
) {
    public static RetrievedMemory empty() {
        return new RetrievedMemory(null, List.of(), List.of());
    }

    public RetrievedMemory {
        inferredPreferences = inferredPreferences == null ? List.of() : List.copyOf(inferredPreferences);
        retrievedMemoryIds = retrievedMemoryIds == null ? List.of() : List.copyOf(retrievedMemoryIds);
    }
}
