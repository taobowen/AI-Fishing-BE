package com.aifishing.guidance.contracts;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.reliability.ValidationSafetyClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.aifishing.guidance.contracts.GuidanceContractSamples.AT;
import static com.aifishing.guidance.contracts.GuidanceContractSamples.ID_A;
import static com.aifishing.guidance.contracts.GuidanceContractSamples.ID_B;
import static org.assertj.core.api.Assertions.assertThat;

class Phase3SharedContractsTest {

    @Test
    void schemaVersionStaysV1() {
        assertThat(GuidanceSchemaVersion.VALUE).isEqualTo("guidance.contracts.v1");
    }

    @Test
    void validationChecksIncludeCooldownAndOscillation() {
        assertThat(DecisionValidationCheck.values()).contains(
                DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN,
                DecisionValidationCheck.MOVE_OSCILLATION
        );
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN)).isFalse();
        assertThat(ValidationSafetyClassifier.isUnsafe(DecisionValidationCheck.MOVE_OSCILLATION)).isFalse();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN))
                .isFalse();
        assertThat(ValidationSafetyClassifier.isInvalidWaypoint(DecisionValidationCheck.MOVE_OSCILLATION)).isFalse();
    }

    @Test
    void missingActiveTargetAndRevisitSliceDeserializeAbsent() throws Exception {
        String oldPlan = """
                {"originalTripPlanId":"%s","currentGuidancePlanVersion":1,"currentStep":1,"shortHorizonSteps":[]}
                """.formatted(ID_A);
        FishingSessionState.Plan plan = GuidanceContracts.mapper().readValue(oldPlan, FishingSessionState.Plan.class);
        assertThat(plan.activeGuidanceTargetTripWaypointId()).isNull();
        assertThat(plan.originalPlanSteps()).isEmpty();

        String oldState = """
                {
                  "schemaVersion":"guidance.contracts.v1",
                  "session":{"sessionId":"%s","userId":"%s","status":"ACTIVE","startedAt":"%s"},
                  "position":{"latitudeWgs84":44.75,"longitudeWgs84":-78.92},
                  "boat":{},
                  "fishing":{},
                  "environment":{},
                  "recent":{"moveIds":[],"lureChangeIds":[],"catchEventIds":[],"adviceIds":[],"rejectedAdviceIds":[],"summaries":[]},
                  "performance":{"catchesThisSession":0},
                  "plan":{"originalTripPlanId":"%s","currentGuidancePlanVersion":1,"currentStep":1,"shortHorizonSteps":[]}
                }
                """.formatted(ID_A, ID_B, AT, ID_A);
        FishingSessionState state = GuidanceContracts.mapper().readValue(oldState, FishingSessionState.class);
        assertThat(state.plan().activeGuidanceTargetTripWaypointId()).isNull();
        assertThat(state.opportunityRevisit().packageCooldowns()).isEmpty();
        assertThat(state.opportunityRevisit().lastMoveTargetTripWaypointIds()).isEmpty();
    }

    @Test
    void opportunityRevisitCapsAtEightRecentEntries() {
        List<FishingSessionState.PackageCooldown> cooled = new ArrayList<>();
        List<UUID> moves = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> {
            UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-%012d".formatted(i));
            cooled.add(new FishingSessionState.PackageCooldown(List.of(id), AT.plusSeconds(i), null));
            moves.add(id);
        });
        FishingSessionState.OpportunityRevisit slice = new FishingSessionState.OpportunityRevisit(cooled, moves);
        assertThat(slice.packageCooldowns()).hasSize(FishingSessionState.OpportunityRevisit.MAX_ENTRIES);
        assertThat(slice.lastMoveTargetTripWaypointIds()).hasSize(FishingSessionState.OpportunityRevisit.MAX_ENTRIES);
        assertThat(slice.packageCooldowns().getFirst().packageMemberIds())
                .containsExactly(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002"));
        assertThat(slice.lastMoveTargetTripWaypointIds().getLast())
                .isEqualTo(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000009"));
    }

    @Test
    void opportunityRevisitPropertiesDefaultToDocumentedKnobs() {
        GuidanceProperties.OpportunityRevisit cfg = new GuidanceProperties().getOpportunityRevisit();
        assertThat(cfg.getPointCooldownMinutes()).isEqualTo(45);
        assertThat(cfg.getPathCooldownMinutes()).isEqualTo(45);
        assertThat(cfg.getZonePackageCooldownMinutes()).isEqualTo(60);
        assertThat(cfg.getOscillationWindowMoves()).isEqualTo(3);
        GuidanceProperties.OpportunityRevisit.MaterialEligibility material = cfg.getMaterialEligibility();
        assertThat(material.isCooldownExpiry()).isTrue();
        assertThat(material.isTimeBucketChange()).isTrue();
        assertThat(material.isWeatherChange()).isTrue();
        assertThat(material.isLivePressureChange()).isTrue();
        assertThat(material.isNewToolEvidence()).isTrue();
        assertThat(material.isExplicitUserIntent()).isTrue();
        assertThat(material.isBiteOrFishOnAtReturnTarget()).isFalse();
        assertThat(material.getTimeChangeMinutes()).isEqualTo(60);
        assertThat(material.getWindSpeedChangeKph()).isEqualTo(10.0);
        assertThat(material.getPressureChangeHpa()).isEqualTo(2.0);
    }

    @Test
    void populatedRevisitSliceIsSchemaValid() {
        FishingSessionState.OpportunityRevisit slice = new FishingSessionState.OpportunityRevisit(
                List.of(new FishingSessionState.PackageCooldown(List.of(ID_A), AT.plusSeconds(2700), ID_B)),
                List.of(ID_A, ID_B)
        );
        FishingSessionState.Plan plan = new FishingSessionState.Plan(
                ID_A, 1, 1, List.of(), List.of(), ID_B
        );
        JsonNode sliceJson = GuidanceContracts.mapper().valueToTree(slice);
        JsonNode planJson = GuidanceContracts.mapper().valueToTree(plan);
        assertThat(GuidanceContracts.schema("OpportunityRevisitSlice").validate(sliceJson)).isEmpty();
        assertThat(GuidanceContracts.schema("PlanStateSlice").validate(planJson)).isEmpty();
        assertThat(planJson.path("activeGuidanceTargetTripWaypointId").asText()).isEqualTo(ID_B.toString());
    }
}
