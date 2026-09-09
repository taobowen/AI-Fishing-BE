package com.aifishing.planning.tactics;

import java.util.List;
import java.util.UUID;

public record TacticalRecommendation(
        UUID visitId,
        TacticProfile idealTactic,
        List<TacticProfile> acceptableAlternativeTactics,
        UUID bestLockerLureId,
        LockerSnapshot lockerSnapshot,
        LockerTechnique lockerTechnique,
        boolean idealAlreadyOwned,
        boolean showIdealOption,
        String warning
) {
    public TacticalRecommendation {
        acceptableAlternativeTactics = acceptableAlternativeTactics == null
                ? List.of()
                : List.copyOf(acceptableAlternativeTactics);
    }
}
