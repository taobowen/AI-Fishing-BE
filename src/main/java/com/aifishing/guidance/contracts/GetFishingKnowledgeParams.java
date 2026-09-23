package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;

public record GetFishingKnowledgeParams(String query, FishSpecies targetSpecies) {
}
