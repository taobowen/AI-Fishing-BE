package com.aifishing.strategy.domain;

import com.aifishing.common.enums.FishSpecies;

public record TargetSpeciesPriority(
        FishSpecies species,
        int priority
) {
}
