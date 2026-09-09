package com.aifishing.planning.tactics;

import java.util.List;
import java.util.UUID;

public record StopTacticalProfile(
        UUID visitId,
        TacticProfile idealTactic,
        List<TacticProfile> acceptableAlternativeTactics
) {
    public StopTacticalProfile {
        acceptableAlternativeTactics = acceptableAlternativeTactics == null
                ? List.of()
                : List.copyOf(acceptableAlternativeTactics);
    }
}
