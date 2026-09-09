package com.aifishing.boat.capability;

import com.aifishing.boat.domain.Boat;

import java.util.List;
import java.util.Map;

public interface BoatCapabilityReasoner {

    Result estimate(Boat boat, List<String> previousValidationErrors);

    default Result estimate(Boat boat, List<String> previousValidationErrors, BoatCapabilityPriors priors) {
        return estimate(boat, previousValidationErrors);
    }

    record Result(
            AiBoatCapabilityEstimate estimate,
            boolean webSearchUsed,
            String modelId,
            Map<String, Object> evidenceMetadata,
            Map<String, Object> rawMetadata
    ) {
    }
}
