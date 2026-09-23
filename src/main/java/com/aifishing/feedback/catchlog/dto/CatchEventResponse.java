package com.aifishing.feedback.catchlog.dto;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.domain.SizeBucket;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record CatchEventResponse(
        UUID id,
        String clientCatchId,
        UUID fishingSessionId,
        UUID tripId,
        UUID tripPlanId,
        UUID tripWaypointId,
        UUID adHocFishingStopId,
        UUID lakeFeatureId,
        UUID fishingTargetId,
        UUID zoneId,
        UUID subtargetId,
        Double alongTrackFraction,
        Instant occurredAt,
        GeoPointDto location,
        Double accuracyM,
        CatchAssociationMethod associationMethod,
        Double distanceToWaypointM,
        CatchStatus status,
        CatchOutcome outcome,
        FishSpecies species,
        Double lengthCm,
        Double weightKg,
        TechniqueType techniqueType,
        String lureName,
        String notes,
        @JsonProperty("isTargetSpecies") Boolean isTargetSpecies,
        SizeBucket sizeBucket,
        FeatureType plannedFeatureType,
        FishSpecies primaryTargetSpecies
) {
}
