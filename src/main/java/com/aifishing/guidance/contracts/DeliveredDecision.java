package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;

import java.util.List;
import java.util.UUID;

public record DeliveredDecision(
        String schemaVersion,
        UUID decisionId,
        GuidanceAction primaryAction,
        GuidanceAction secondaryAction,
        UUID targetTripWaypointId,
        LureFamily suggestedLure,
        PresentationTechnique suggestedPresentation,
        Double depthMinM,
        Double depthMaxM,
        RetrieveStyle retrieveStyle,
        int reevaluateAfterMinutes,
        List<String> reasonCodes,
        String shortExplanation,
        List<HorizonStep> proposedHorizon,
        boolean fallbackUsed,
        String fallbackReason,
        Double systemConfidence
) {
}
