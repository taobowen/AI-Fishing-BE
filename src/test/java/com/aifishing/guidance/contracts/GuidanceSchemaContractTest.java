package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.aifishing.guidance.contracts.GuidanceContractSamples.AT;
import static com.aifishing.guidance.contracts.GuidanceContractSamples.ID_A;
import static com.aifishing.guidance.contracts.GuidanceContractSamples.ID_B;
import static com.aifishing.guidance.contracts.GuidanceContractSamples.json;
import static org.assertj.core.api.Assertions.assertThat;

class GuidanceSchemaContractTest {

    @Test
    void everyDefCompilesAndHasAValidFixture() {
        GuidanceContracts.bundle().path("$defs").fieldNames().forEachRemaining(name -> {
            JsonNode fixture = fixtures().get(name);
            assertThat(fixture)
                    .withFailMessage("missing valid fixture for $defs/%s", name)
                    .isNotNull();
            assertValid(name, fixture);
        });
    }

    @Test
    void unknownActionIsRejected() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.candidate());
        node.put("primaryAction", "TROLL_RANDOMLY");
        assertInvalid("CandidateDecision", node);
    }

    @Test
    void persistedTypesRequireSchemaVersion() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.candidate());
        node.remove("schemaVersion");
        assertInvalid("CandidateDecision", node);
    }

    @Test
    void targetWaypointIdIsRejected() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.candidate());
        node.put("targetWaypointId", ID_B.toString());
        assertInvalid("CandidateDecision", node);
    }

    @Test
    void unitlessDepthIsRejected() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.candidate());
        node.put("depth", 3.5);
        assertInvalid("CandidateDecision", node);
    }

    @Test
    void nonZTimestampIsRejected() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.sessionEvent());
        node.put("occurredAt", "2026-09-16T13:33:00-04:00");
        assertInvalid("SessionEvent", node);
    }

    @Test
    void deliveredRejectsBareConfidenceAndModelConfidence() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.delivered());
        node.put("confidence", 0.76);
        assertInvalid("DeliveredDecision", node);

        ObjectNode copied = (ObjectNode) json(GuidanceContractSamples.delivered());
        copied.put("modelConfidence", 0.76);
        copied.put("systemConfidence", 0.76);
        assertInvalid("DeliveredDecision", copied);
    }

    @Test
    void structuredOutputSchemaIsOpenAiNameSafeAndReachableOnly() {
        JsonNode schema = GuidanceContracts.structuredOutputSchema("CandidateDecision");
        assertThat(schema.has("$id")).isFalse();
        assertThat(schema.has("$schema")).isFalse();
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("primaryAction")).isTrue();
        assertThat(schema.path("$defs").has("CandidateDecision")).isFalse();
        assertThat(schema.path("$defs").has("GuidanceAction")).isTrue();
        assertThat(schema.path("$defs").has("HorizonStep")).isTrue();
        assertThat(schema.path("$defs").has("SafetyVerdict")).isFalse();
        assertThat(schema.toString()).doesNotContain("aifishing.local");
        schema.path("$defs").fieldNames().forEachRemaining(name ->
                assertThat(name).matches("^[a-zA-Z0-9_-]{1,64}$"));
    }

    @Test
    void candidateAllowsModelConfidenceAndRejectsSystemConfidence() {
        assertValid("CandidateDecision", json(GuidanceContractSamples.candidate()));
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.candidate());
        node.put("systemConfidence", 0.4);
        assertInvalid("CandidateDecision", node);
    }

    @Test
    void arbitraryClosedActionPairsAreSchemaLegal() {
        assertValid("CandidateDecision", json(GuidanceContractSamples.candidate(GuidanceAction.RETURN, GuidanceAction.MOVE)));
        assertValid("CandidateDecision", json(GuidanceContractSamples.candidate(GuidanceAction.STAY, GuidanceAction.STAY)));
        assertValid("CandidateDecision", json(GuidanceContractSamples.candidate(GuidanceAction.CHANGE_DEPTH, GuidanceAction.RETURN)));
    }

    @Test
    void nearbyWaypointParamsMatchDisambiguatedContract() {
        GetNearbyWaypointsParams params = new GetNearbyWaypointsParams(44.75, -78.92, 400, FishSpecies.SMALLMOUTH_BASS);
        assertValid("GetNearbyWaypointsParams", json(params));
        ObjectNode tooSmall = (ObjectNode) json(params);
        tooSmall.put("radiusMeters", 10);
        assertInvalid("GetNearbyWaypointsParams", tooSmall);
        ObjectNode latLng = (ObjectNode) json(params);
        latLng.remove("latitudeWgs84");
        latLng.put("latitude", 44.75);
        assertInvalid("GetNearbyWaypointsParams", latLng);
    }

    @Test
    void originalPlanStepsAreOptionalAndReplayToEmptyList() throws Exception {
        ObjectNode plan = (ObjectNode) json(GuidanceContractSamples.state().plan());
        plan.remove("originalPlanSteps");
        assertValid("PlanStateSlice", plan);
        FishingSessionState.Plan readPlan = GuidanceContracts.mapper().treeToValue(plan, FishingSessionState.Plan.class);
        assertThat(readPlan.originalPlanSteps()).isEmpty();

        ObjectNode context = (ObjectNode) json(GuidanceContractSamples.context());
        context.remove("originalPlanSteps");
        assertValid("FishingAgentContext", context);
        FishingAgentContext readContext = GuidanceContracts.mapper().treeToValue(context, FishingAgentContext.class);
        assertThat(readContext.originalPlanSteps()).isEmpty();
    }

    @Test
    void activeGuidanceTargetAndOpportunityRevisitAreOptional() throws Exception {
        ObjectNode plan = (ObjectNode) json(GuidanceContractSamples.state().plan());
        plan.remove("activeGuidanceTargetTripWaypointId");
        assertValid("PlanStateSlice", plan);
        FishingSessionState.Plan readPlan = GuidanceContracts.mapper().treeToValue(plan, FishingSessionState.Plan.class);
        assertThat(readPlan.activeGuidanceTargetTripWaypointId()).isNull();

        ObjectNode state = (ObjectNode) json(GuidanceContractSamples.state());
        state.remove("opportunityRevisit");
        state.with("plan").remove("activeGuidanceTargetTripWaypointId");
        assertValid("FishingSessionState", state);
        FishingSessionState readState = GuidanceContracts.mapper().treeToValue(state, FishingSessionState.class);
        assertThat(readState.plan().activeGuidanceTargetTripWaypointId()).isNull();
        assertThat(readState.opportunityRevisit().packageCooldowns()).isEmpty();
        assertThat(readState.opportunityRevisit().lastMoveTargetTripWaypointIds()).isEmpty();
    }

    @Test
    void adHocFishingFieldsAreOptionalOnStateAndSituation() throws Exception {
        ObjectNode fishing = (ObjectNode) json(GuidanceContractSamples.state().fishing());
        fishing.remove("adHocFishingStopId");
        fishing.remove("adHocStartedAt");
        fishing.remove("fishingTargetId");
        fishing.remove("physicalZoneId");
        fishing.remove("lakeFeatureId");
        assertValid("FishingStateSlice", fishing);
        FishingSessionState.Fishing readFishing = GuidanceContracts.mapper()
                .treeToValue(fishing, FishingSessionState.Fishing.class);
        assertThat(readFishing.adHocFishingStopId()).isNull();
        assertThat(readFishing.adHocStartedAt()).isNull();

        ObjectNode situation = (ObjectNode) json(GuidanceContractSamples.context().currentSituation());
        situation.remove("activityState");
        situation.remove("activityStateSource");
        situation.remove("adHocFishingStopId");
        situation.remove("adHocStartedAt");
        situation.remove("fishingTargetId");
        situation.remove("physicalZoneId");
        situation.remove("lakeFeatureId");
        assertValid("CurrentSituation", situation);
        FishingAgentContext.CurrentSituation readSituation = GuidanceContracts.mapper()
                .treeToValue(situation, FishingAgentContext.CurrentSituation.class);
        assertThat(readSituation.adHocFishingStopId()).isNull();
        assertThat(readSituation.activityState()).isNull();
    }

    @Test
    void safetyVerdictBlockRequiresPrescribedAction() {
        assertValid("SafetyVerdict", json(GuidanceContractSamples.safetyBlock()));

        ObjectNode missing = (ObjectNode) json(GuidanceContractSamples.safetyBlock());
        missing.remove("prescribedAction");
        assertInvalid("SafetyVerdict", missing);

        ObjectNode nulled = (ObjectNode) json(GuidanceContractSamples.safetyBlock());
        nulled.putNull("prescribedAction");
        assertInvalid("SafetyVerdict", nulled);
    }

    @Test
    void safetyVerdictOkRejectsPrescribedAction() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.safetyOk());
        node.put("prescribedAction", GuidanceAction.STAY.name());
        assertInvalid("SafetyVerdict", node);
    }

    @Test
    void agentRunStatusIncludesRunningAndTimeout() {
        assertThat(GuidanceContracts.def("AgentRunStatus").get("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly("RUNNING", "COMPLETED", "FALLBACK", "FAILED", "TIMEOUT");
        ObjectNode timeout = (ObjectNode) json(GuidanceContractSamples.runResult());
        timeout.put("status", AgentRunStatus.TIMEOUT.name());
        assertValid("AgentRunResult", timeout);
    }

    @Test
    void biteEventAndOptionalFishInteractionIdAreValid() {
        assertThat(GuidanceContracts.def("SessionEventType").get("enum").valueStream().map(JsonNode::asText).toList())
                .contains("BITE");
        assertValid("SessionEvent", json(GuidanceContractSamples.sessionEvent()));
        assertThat(json(GuidanceContractSamples.sessionEvent()).has("fishInteractionId")).isFalse();
        assertValid("SessionEvent", json(GuidanceContractSamples.biteEvent()));
        assertThat(json(GuidanceContractSamples.biteEvent()).path("fishInteractionId").asText())
                .isEqualTo(ID_A.toString());
    }

    @Test
    void fishingActivityAllowsUnknownAndDoesNotRequireNewFields() {
        assertThat(GuidanceContracts.def("FishingActivityState").get("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly("TRANSIT", "FISHING", "PAUSED", "UNKNOWN");
        ObjectNode fishing = (ObjectNode) json(GuidanceContractSamples.state().fishing());
        fishing.put("activityState", FishingActivityState.UNKNOWN.name());
        fishing.put("activityStateSource", ActivityStateSource.UNKNOWN.name());
        assertValid("FishingStateSlice", fishing);

        ObjectNode omitted = (ObjectNode) json(GuidanceContractSamples.state().fishing());
        omitted.remove("activityState");
        omitted.remove("activityStateSince");
        omitted.remove("activityStateSource");
        omitted.remove("activeFishingEffortMinutes");
        assertValid("FishingStateSlice", omitted);
    }

    @Test
    void triggerRoutingDecisionIsPrimaryRelatedReasons() {
        assertValid("TriggerRoutingDecision", json(GuidanceContractSamples.routingDecision()));
        ObjectNode missingPrimary = (ObjectNode) json(GuidanceContractSamples.routingDecision());
        missingPrimary.remove("primary");
        assertInvalid("TriggerRoutingDecision", missingPrimary);
    }

    @Test
    void triggerRoutingDecisionRejectsActionReasonCodes() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TriggerRoutingDecision(
                GuidanceTrigger.NO_BITE_THRESHOLD,
                List.of(),
                List.of(GuidanceAction.MOVE.name())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not encode actions");
    }

    @Test
    void learningJobTypeIncludesOnlineMetricsRollup() {
        assertThat(GuidanceContracts.def("LearningJobType").get("enum").valueStream().map(JsonNode::asText).toList())
                .contains(
                        "ATTRIBUTE_OUTCOME", "AGGREGATE_EMPIRICAL", "SESSION_SUMMARY",
                        "REFLECTION_EVAL", "PREFERENCE_UPDATE", "ONLINE_METRICS_ROLLUP");
    }

    @Test
    void guidanceSuccessKindSeparatesBiteFromFishOnAndOmitsLanding() {
        assertThat(GuidanceContracts.def("GuidanceSuccessKind").get("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly(
                        "FISH_ON_SUCCESS", "BITE_SIGNAL_ONLY", "NO_FISH_SIGNAL", "NOT_FOLLOWED", "UNATTRIBUTED")
                .doesNotContain("CATCH_LANDED", "LANDING", "NO_FISH_SIGNAL_OR_BITE");
    }

    @Test
    void evalSuiteKindIncludesDeferredSimulation() {
        assertThat(GuidanceContracts.def("EvalSuiteKind").get("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly(
                        "PLATFORM_REGRESSION", "AGENT_POLICY_EVAL", "SHADOW_REPLAY", "ONLINE_ROLLUP", "SIMULATION");
    }

    @Test
    void replayModeIsFrozenOrComponentRecompute() {
        assertThat(GuidanceContracts.def("ReplayMode").get("enum").valueStream().map(JsonNode::asText).toList())
                .containsExactly("FROZEN_REPLAY", "COMPONENT_RECOMPUTE");
    }

    @Test
    void evalCaseResultRejectsCounterfactualSuccessField() {
        ObjectNode node = (ObjectNode) json(GuidanceContractSamples.evalCaseResult());
        node.put("counterfactualSuccess", true);
        assertInvalid("EvalCaseResult", node);
        node.remove("counterfactualSuccess");
        node.put("shadowCounterfactualSuccess", true);
        assertInvalid("EvalCaseResult", node);
    }

    @Test
    void usageTelemetryOmitsUnknownTokensInsteadOfZero() {
        UsageTelemetry unknown = GuidanceContractSamples.usageTelemetryUnknown();
        JsonNode node = json(unknown);
        assertThat(node.has("inputTokens")).isFalse();
        assertThat(node.has("outputTokens")).isFalse();
        assertThat(node.has("totalTokens")).isFalse();
        assertThat(node.has("costUsd")).isFalse();
        assertValid("UsageTelemetry", node);
        UsageTelemetry costWithoutPrice = new UsageTelemetry(
                GuidanceSchemaVersion.VALUE, "openai", "gpt-4o", "v1", "prompt-1",
                10, 5, 15, 0.02, null
        );
        JsonNode stripped = json(costWithoutPrice);
        assertThat(stripped.has("costUsd")).isFalse();
        assertThat(costWithoutPrice.costUsd()).isNull();
        assertValid("UsageTelemetry", stripped);
    }

    @Test
    void evalCoverageZeroAttemptClearsCoverage() {
        EvalCoverage empty = new EvalCoverage(0, 0, 0, 0, 0.0);
        assertThat(empty.evalCoverage()).isNull();
        assertValid("EvalCoverage", json(empty));
        EvalCoverage computed = new EvalCoverage(3, 1, 4, 2, 0.99);
        assertThat(computed.evalCoverage()).isEqualTo(0.75);
        assertValid("EvalCoverage", json(computed));
    }

    @Test
    void windDirectionBucketIsIndependentOfCompassDirection() {
        List<String> compass = GuidanceContracts.def("CompassDirection").get("enum").valueStream()
                .map(JsonNode::asText).toList();
        List<String> windDir = GuidanceContracts.def("WindDirectionBucket").get("enum").valueStream()
                .map(JsonNode::asText).toList();
        assertThat(compass).doesNotContain("VARIABLE");
        assertThat(windDir).contains("VARIABLE");
        assertThat(windDir).containsAll(compass);
    }

    @Test
    void outcomeAttributionUsesDimensionLocalFollow() {
        assertValid("OutcomeAttribution", json(new OutcomeAttribution(
                GuidanceSchemaVersion.VALUE, ID_A, ID_B, ID_A, AttributionDimension.LURE,
                true, RecommendationRole.SECONDARY, OutcomeKind.FISH_ON, AttributionWindowKind.LURE, 0.7, AT
        )));
        ObjectNode node = (ObjectNode) json(new OutcomeAttribution(
                GuidanceSchemaVersion.VALUE, ID_A, ID_B, ID_A, AttributionDimension.LOCATION,
                true, RecommendationRole.PRIMARY, OutcomeKind.FISH_ON, AttributionWindowKind.MOVE, 0.8, AT
        ));
        node.remove("followedRecommendation");
        node.put("followedPrimary", true);
        assertInvalid("OutcomeAttribution", node);
    }

    @Test
    void historicalPerformanceKeepsFishSignalAliases() {
        HistoricalPerformance row = new HistoricalPerformance(
                GuidanceSchemaVersion.VALUE, ID_A, null, ID_B, FishSpecies.SMALLMOUTH_BASS,
                SeasonBucket.SUMMER, TimeBucket.MORNING, FeatureType.POINT, WindBucket.CALM,
                WindDirectionBucket.VARIABLE, LureFamily.TUBE, 40.0, 0, 0.0, 0.4, 1, 0.6, AT,
                2400L, 4, 2, 0, 1, 1.0, 0.5, 0.0, 1
        );
        assertThat(row.catchCount()).isEqualTo(row.landedCount()).isZero();
        assertThat(row.sampleSize()).isEqualTo(row.contributingSessionWaypointCount()).isEqualTo(1);
        assertThat(row.biteCount()).isEqualTo(4);
        assertThat(row.fishOnCount()).isEqualTo(2);
        assertThat(row.empiricalAlgorithmVersion()).isEqualTo(1);
        assertValid("HistoricalPerformance", json(row));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new HistoricalPerformance(
                GuidanceSchemaVersion.VALUE, ID_A, null, ID_B, FishSpecies.SMALLMOUTH_BASS,
                SeasonBucket.SUMMER, TimeBucket.MORNING, FeatureType.POINT, WindBucket.CALM,
                WindDirectionBucket.N, LureFamily.TUBE, 40.0, 2, 0.0, 0.4, 1, 0.6, AT,
                2400L, 4, 2, 0, 1, 1.0, 0.5, 0.0, 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("landedCount must equal catchCount");
    }

    @Test
    void httpHintsMayOmitSchemaVersion() {
        GuidanceDecisionRequest request = new GuidanceDecisionRequest(
                GuidanceTrigger.USER_REQUEST,
                "try the point",
                new GuidanceClientHints(44.75, -78.92, 5.0, 180.0, 0.4)
        );
        assertValid("GuidanceDecisionRequest", json(request));
        assertThat(json(request).has("schemaVersion")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidCases")
    void invalidFixturesFail(String defName, JsonNode node) {
        assertInvalid(defName, node);
    }

    static Stream<Arguments> invalidCases() {
        ObjectNode missingStateVersion = (ObjectNode) json(GuidanceContractSamples.state());
        missingStateVersion.remove("schemaVersion");
        ObjectNode stackTrace = (ObjectNode) json(GuidanceContractSamples.toolResult());
        ((ObjectNode) stackTrace.get("data")).put("stackTrace", "java.lang.RuntimeException");
        return Stream.of(
                Arguments.of("FishingSessionState", missingStateVersion),
                Arguments.of("ToolResultEnvelope", stackTrace)
        );
    }

    private static Map<String, JsonNode> fixtures() {
        Map<String, JsonNode> fixtures = new LinkedHashMap<>();
        JsonNode defs = GuidanceContracts.bundle().path("$defs");
        defs.fieldNames().forEachRemaining(name -> {
            JsonNode def = defs.get(name);
            if (def.has("const")) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().textNode(def.get("const").asText()));
                return;
            }
            if (def.has("enum")) {
                fixtures.put(name, def.get("enum").get(0));
                return;
            }
            if ("Uuid".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().textNode(ID_A.toString()));
                return;
            }
            if ("InstantUtc".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().textNode(AT.toString()));
                return;
            }
            if ("LatitudeWgs84".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().numberNode(44.75));
                return;
            }
            if ("LongitudeWgs84".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().numberNode(-78.92));
                return;
            }
            if ("UnitInterval".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().getNodeFactory().numberNode(0.5));
                return;
            }
            if ("JsonObject".equals(name)) {
                fixtures.put(name, GuidanceContracts.mapper().createObjectNode().put("ok", true));
            }
        });
        fixtures.put("HorizonStep", json(GuidanceContractSamples.committedMove()));
        fixtures.put("OriginalPlanStep", json(GuidanceContractSamples.originalPlanStep()));
        fixtures.put("SessionEvent", json(GuidanceContractSamples.sessionEvent()));
        fixtures.put("TriggerRoutingDecision", json(GuidanceContractSamples.routingDecision()));
        fixtures.put("SessionStateSlice", json(GuidanceContractSamples.state().session()));
        fixtures.put("PositionStateSlice", json(GuidanceContractSamples.state().position()));
        fixtures.put("BoatStateSlice", json(GuidanceContractSamples.state().boat()));
        fixtures.put("FishingStateSlice", json(GuidanceContractSamples.state().fishing()));
        fixtures.put("EnvironmentStateSlice", json(GuidanceContractSamples.state().environment()));
        fixtures.put("RecentStateSlice", json(GuidanceContractSamples.state().recent()));
        fixtures.put("PerformanceStateSlice", json(GuidanceContractSamples.state().performance()));
        fixtures.put("PlanStateSlice", json(GuidanceContractSamples.state().plan()));
        fixtures.put("PackageCooldown", json(GuidanceContractSamples.packageCooldown()));
        fixtures.put("OpportunityRevisitSlice", json(GuidanceContractSamples.state().opportunityRevisit()));
        fixtures.put("FishingSessionState", json(GuidanceContractSamples.state()));
        fixtures.put("CurrentSituation", json(GuidanceContractSamples.context().currentSituation()));
        fixtures.put("ContextWeather", json(GuidanceContractSamples.context().weather()));
        fixtures.put("FishingAgentContext", json(GuidanceContractSamples.context()));
        fixtures.put("CandidateDecision", json(GuidanceContractSamples.candidate()));
        fixtures.put("DeliveredDecision", json(GuidanceContractSamples.delivered()));
        fixtures.put("ValidationIssue", json(new ValidationIssue(DecisionValidationCheck.ACTION_INCOMPATIBLE, "not yet implemented")));
        fixtures.put("DecisionValidationResult", json(GuidanceContractSamples.validation()));
        fixtures.put("SafetyVerdict", json(GuidanceContractSamples.safetyOk()));
        fixtures.put("GetNearbyWaypointsParams", json(new GetNearbyWaypointsParams(44.75, -78.92, 250, FishSpecies.WALLEYE)));
        fixtures.put("GetWaypointStructureParams", json(new GetWaypointStructureParams(ID_B)));
        fixtures.put("GetLiveWaypointActivityParams", json(new GetLiveWaypointActivityParams(ID_B, 100)));
        fixtures.put("GetHistoricalPerformanceParams", json(new GetHistoricalPerformanceParams(ID_A, ID_B, FishSpecies.SMALLMOUTH_BASS, LureFamily.TUBE)));
        fixtures.put("GetFishingKnowledgeParams", json(new GetFishingKnowledgeParams("rocky points in west wind", FishSpecies.SMALLMOUTH_BASS)));
        fixtures.put("GetAlternativeRouteParams", json(new GetAlternativeRouteParams(44.75, -78.92, ID_B, 8000.0)));
        fixtures.put("ToolRequestEnvelope", json(GuidanceContractSamples.toolRequest()));
        fixtures.put("ToolResultEnvelope", json(GuidanceContractSamples.toolResult()));
        fixtures.put("ToolCallRecord", json(GuidanceContractSamples.toolCall()));
        fixtures.put("AgentRunRequest", json(GuidanceContractSamples.runRequest()));
        fixtures.put("AgentRunResult", json(GuidanceContractSamples.runResult()));
        fixtures.put("GuidanceClientHints", json(new GuidanceClientHints(44.75, -78.92, 4.0, 90.0, 0.5)));
        fixtures.put("GuidanceDecisionRequest", json(new GuidanceDecisionRequest(GuidanceTrigger.FISH_ON, null, null)));
        fixtures.put("GuidanceCurrentResponse", json(new GuidanceCurrentResponse(ID_A, GuidanceContractSamples.delivered())));
        fixtures.put("GuidanceFeedbackRequest", json(new GuidanceFeedbackRequest(FeedbackStatus.ACCEPTED, null, "good")));
        fixtures.put("WeatherSnapshot", json(new WeatherSnapshot(GuidanceSchemaVersion.VALUE, AT, WeatherCondition.CLEAR, 12.0, CompassDirection.N, 17.0, 1015.0)));
        fixtures.put("LureEvent", json(new LureEvent(GuidanceSchemaVersion.VALUE, AT, LureFamily.JERKBAIT, PresentationTechnique.TWITCH_PAUSE, 3.5, RetrieveStyle.SLOW)));
        fixtures.put("UserActionEvent", json(new UserActionEvent(
                GuidanceSchemaVersion.VALUE, AT, ID_A, true, GuidanceAction.MOVE, Map.of("note", "went"),
                true, RecommendationRole.PRIMARY
        )));
        fixtures.put("GuidancePlanStep", json(new GuidancePlanStep(GuidanceSchemaVersion.VALUE, 1, GuidanceAction.STAY, true, ID_B, 10)));
        fixtures.put("GuidancePlanVersion", json(new GuidancePlanVersion(
                GuidanceSchemaVersion.VALUE, 2, 1, "weather", ReplanScope.REGIONAL, PlanCreatedBy.AGENT, AT,
                List.of(new GuidancePlanStep(GuidanceSchemaVersion.VALUE, 1, GuidanceAction.RETURN, true, null, 20))
        )));
        fixtures.put("HistoricalPerformance", json(new HistoricalPerformance(
                GuidanceSchemaVersion.VALUE, ID_A, null, ID_B, FishSpecies.SMALLMOUTH_BASS,
                SeasonBucket.SUMMER, TimeBucket.MORNING, FeatureType.POINT, WindBucket.MODERATE,
                WindDirectionBucket.W, LureFamily.TUBE, 40.0, 2, 3.0, 0.4, 8, 0.6, AT,
                2400L, 4, 2, 2, 8, 1.0, 0.5, 1.0, 1
        )));
        fixtures.put("SemanticMemory", json(new SemanticMemory(
                GuidanceSchemaVersion.VALUE, ID_A, ID_B, SemanticMemoryKind.USER_PREFERENCE_NOTE,
                "avoids long runs in wind", null, AT
        )));
        fixtures.put("UserFishingPreferences", json(new UserFishingPreferences(
                GuidanceSchemaVersion.VALUE, ID_A, true, List.of(TechniqueType.JERKBAIT), List.of(TechniqueType.TROLLING), 800.0, 0.8
        )));
        fixtures.put("SessionSummary", json(new SessionSummary(GuidanceSchemaVersion.VALUE, ID_A, "Quiet morning.", List.of("no bites"), AT)));
        fixtures.put("AgentReflection", json(new AgentReflection(
                GuidanceSchemaVersion.VALUE, ID_A, ID_B, ReflectionClaimKind.SESSION_HYPOTHESIS,
                ReflectionCauseKind.STRATEGY_FAILURE, "presentation may be the issue", AT
        )));
        fixtures.put("LiveWaypointActivity", json(new LiveWaypointActivity(
                GuidanceSchemaVersion.VALUE, ID_B, 300, 0, 0, LiveWaypointPressure.LOW, AT
        )));
        fixtures.put("OutcomeAttribution", json(new OutcomeAttribution(
                GuidanceSchemaVersion.VALUE, ID_A, ID_B, ID_A, AttributionDimension.LOCATION,
                true, RecommendationRole.PRIMARY, OutcomeKind.FISH_ON, AttributionWindowKind.MOVE, 0.8, AT
        )));
        fixtures.put("InferredUserPreference", json(new InferredUserPreference(
                GuidanceSchemaVersion.VALUE, ID_A, "maxMoveMeters", "800", 1, 0.2, AT, AT
        )));
        fixtures.put("RetrievedMemory", json(new RetrievedMemory(null, List.of(), List.of("mem-1"))));
        fixtures.put("EvalComponentVersions", json(GuidanceContractSamples.evalComponentVersions()));
        fixtures.put("EvalCoverage", json(GuidanceContractSamples.evalCoverage()));
        fixtures.put("EvalRun", json(GuidanceContractSamples.evalRun()));
        fixtures.put("EvalCaseResult", json(GuidanceContractSamples.evalCaseResult()));
        fixtures.put("OnlineGuidanceMetrics", json(GuidanceContractSamples.onlineGuidanceMetrics()));
        fixtures.put("UsageTelemetry", json(GuidanceContractSamples.usageTelemetryUnknown()));
        fixtures.put("FrozenAgentRunSnapshot", json(GuidanceContractSamples.frozenSnapshot()));
        return fixtures;
    }

    private static void assertValid(String defName, JsonNode node) {
        Set<ValidationMessage> errors = GuidanceContracts.schema(defName).validate(node);
        assertThat(errors)
                .withFailMessage("%s should be valid: %s", defName, errors)
                .isEmpty();
    }

    private static void assertInvalid(String defName, JsonNode node) {
        Set<ValidationMessage> errors = GuidanceContracts.schema(defName).validate(node);
        assertThat(errors)
                .withFailMessage("%s should be invalid: %s", defName, node)
                .isNotEmpty();
    }
}
