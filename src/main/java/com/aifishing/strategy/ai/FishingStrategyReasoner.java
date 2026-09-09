package com.aifishing.strategy.ai;

import com.aifishing.strategy.context.FishingContext;
import com.aifishing.strategy.domain.FishingStrategyProfile;

import java.util.List;
import java.util.Map;

public interface FishingStrategyReasoner {

    StrategyReasonerResult reason(FishingContext context, List<String> previousValidationErrors);

    record StrategyReasonerResult(
            FishingStrategyProfile profile,
            Map<String, Object> usageMetadata,
            Map<String, Object> sourceMetadata
    ) {
        public StrategyReasonerResult {
            usageMetadata = usageMetadata == null ? Map.of() : Map.copyOf(usageMetadata);
            sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
        }
    }
}
