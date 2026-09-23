package com.aifishing.guidance.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceRecordRoundTripTest {

    @ParameterizedTest
    @MethodSource("records")
    void javaRecordRoundTripsThroughCanonicalSchema(String defName, Object value) throws Exception {
        JsonNode tree = GuidanceContracts.mapper().valueToTree(value);
        assertThat(GuidanceContracts.schema(defName).validate(tree)).isEmpty();
        Object read = GuidanceContracts.mapper().treeToValue(tree, value.getClass());
        JsonNode roundTrip = GuidanceContracts.mapper().valueToTree(read);
        assertThat(roundTrip).isEqualTo(tree);
        assertThat(GuidanceContracts.schema(defName).validate(roundTrip)).isEmpty();
    }

    static Stream<Arguments> records() {
        return Stream.of(
                Arguments.of("FishingSessionState", GuidanceContractSamples.state()),
                Arguments.of("FishingAgentContext", GuidanceContractSamples.context()),
                Arguments.of("CandidateDecision", GuidanceContractSamples.candidate()),
                Arguments.of("DeliveredDecision", GuidanceContractSamples.delivered()),
                Arguments.of("SessionEvent", GuidanceContractSamples.sessionEvent()),
                Arguments.of("SessionEvent", GuidanceContractSamples.biteEvent()),
                Arguments.of("TriggerRoutingDecision", GuidanceContractSamples.routingDecision()),
                Arguments.of("ToolCallRecord", GuidanceContractSamples.toolCall()),
                Arguments.of("AgentRunRequest", GuidanceContractSamples.runRequest()),
                Arguments.of("AgentRunResult", GuidanceContractSamples.runResult()),
                Arguments.of("SafetyVerdict", GuidanceContractSamples.safetyOk()),
                Arguments.of("SafetyVerdict", GuidanceContractSamples.safetyBlock()),
                Arguments.of("GuidanceDecisionRequest", new GuidanceDecisionRequest(GuidanceTrigger.USER_REQUEST, "note", null)),
                Arguments.of("GetNearbyWaypointsParams", new GetNearbyWaypointsParams(44.75, -78.92, 50, com.aifishing.common.enums.FishSpecies.WALLEYE)),
                Arguments.of("OutcomeAttribution", new OutcomeAttribution(
                        GuidanceSchemaVersion.VALUE,
                        java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        java.util.UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
                        java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                        AttributionDimension.LOCATION,
                        true,
                        RecommendationRole.PRIMARY,
                        OutcomeKind.FISH_ON,
                        AttributionWindowKind.MOVE,
                        0.8,
                        java.time.Instant.parse("2026-09-16T13:33:00Z")
                )),
                Arguments.of("RetrievedMemory", RetrievedMemory.empty()),
                Arguments.of("EvalRun", GuidanceContractSamples.evalRun()),
                Arguments.of("EvalCaseResult", GuidanceContractSamples.evalCaseResult()),
                Arguments.of("OnlineGuidanceMetrics", GuidanceContractSamples.onlineGuidanceMetrics()),
                Arguments.of("UsageTelemetry", GuidanceContractSamples.usageTelemetryUnknown()),
                Arguments.of("FrozenAgentRunSnapshot", GuidanceContractSamples.frozenSnapshot())
        );
    }

    @ParameterizedTest
    @MethodSource("spiTypes")
    void spiStaysProviderNeutral(Class<?> type) {
        assertThat(type.getPackageName()).isEqualTo("com.aifishing.guidance.spi");
        assertThat(type.getName()).doesNotContain("openai", "bedrock", "anthropic");
    }

    static Stream<Class<?>> spiTypes() {
        return List.of(
                com.aifishing.guidance.spi.FishingAgentFacade.class,
                com.aifishing.guidance.spi.FishingAgentRuntime.class,
                com.aifishing.guidance.spi.FishingSessionStateBuilder.class,
                com.aifishing.guidance.spi.FishingAgentContextBuilder.class,
                com.aifishing.guidance.spi.MemoryRetrievalService.class,
                com.aifishing.guidance.spi.LiveWaypointActivityStore.class,
                com.aifishing.guidance.spi.DecisionValidator.class,
                com.aifishing.guidance.spi.SafetyRuleEngine.class,
                com.aifishing.guidance.spi.AgentTool.class,
                com.aifishing.guidance.spi.AgentToolRegistry.class,
                com.aifishing.guidance.spi.DecisionPersistence.class,
                com.aifishing.guidance.spi.TriggerRouter.class,
                com.aifishing.guidance.spi.DerivedTriggerEvaluator.class,
                com.aifishing.guidance.spi.AgentRunSnapshotLoader.class,
                com.aifishing.guidance.spi.EvalRuntime.class,
                com.aifishing.guidance.spi.EvalToolRegistry.class
        ).stream();
    }
}
