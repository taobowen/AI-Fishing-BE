package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GuidanceContractSamples {

    static final Instant AT = Instant.parse("2026-09-16T13:33:00Z");
    static final UUID ID_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    static final UUID ID_B = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    static final UUID ID_C = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    private GuidanceContractSamples() {
    }

    static HorizonStep committedMove() {
        return new HorizonStep(1, GuidanceAction.MOVE, ID_B, null, true);
    }

    static HorizonStep tentativeStay() {
        return new HorizonStep(2, GuidanceAction.STAY, null, 20, false);
    }

    static OriginalPlanStep originalPlanStep() {
        return new OriginalPlanStep(1, ID_B, ID_A, null, ID_C, List.of(ID_A), "FISHING");
    }

    static FishingSessionState.PackageCooldown packageCooldown() {
        return new FishingSessionState.PackageCooldown(List.of(ID_A), AT.plusSeconds(2700), ID_C);
    }

    static FishingSessionState state() {
        return new FishingSessionState(
                GuidanceSchemaVersion.VALUE,
                new FishingSessionState.Session(
                        ID_A, ID_C, FishingSessionStatus.ACTIVE, AT, FishSpecies.SMALLMOUTH_BASS, 90
                ),
                new FishingSessionState.Position(44.75, -78.92, 4.5, 1.2, 210.0),
                new FishingSessionState.Boat(BoatType.FISHING_BOAT, PropulsionType.GAS_OUTBOARD, 8.0, 12000.0, 1500.0),
                new FishingSessionState.Fishing(
                        ID_B, ID_C, FeatureType.POINT, 3.0, 4.5, LureFamily.JERKBAIT,
                        PresentationTechnique.TWITCH_PAUSE, RetrieveStyle.SLOW, 31,
                        FishingActivityState.FISHING, AT, ActivityStateSource.PROGRESS, 31, 31
                ),
                new FishingSessionState.Environment(
                        WeatherCondition.CLOUDY, 16.0, CompassDirection.W, 1012.0, 18.0, AT, 4
                ),
                new FishingSessionState.Recent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of("No bites since arriving")),
                new FishingSessionState.Performance(0, 0.0, 31, 0),
                new FishingSessionState.Plan(ID_A, 1, 1, List.of(committedMove(), tentativeStay()), List.of(originalPlanStep()))
        );
    }

    static FishingAgentContext context() {
        return new FishingAgentContext(
                GuidanceSchemaVersion.VALUE,
                GuidanceTrigger.NO_BITE_THRESHOLD,
                FishSpecies.SMALLMOUTH_BASS,
                new FishingAgentContext.CurrentSituation(
                        ID_B, ID_C, FeatureType.POINT, 31, 31, LureFamily.JERKBAIT, 3.0, 4.5
                ),
                new FishingAgentContext.ContextWeather(CompassDirection.W, 16.0, 4),
                List.of("Moved to current waypoint 31 minutes ago", "No bites since arriving"),
                null,
                null,
                null,
                List.of(originalPlanStep())
        );
    }

    static CandidateDecision candidate() {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                ID_B,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("CURRENT_SPOT_UNPRODUCTIVE"),
                "Move to the nearby transition and slow the presentation.",
                List.of(committedMove(), tentativeStay()),
                0.76
        );
    }

    static CandidateDecision candidate(GuidanceAction primary, GuidanceAction secondary) {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                primary,
                secondary,
                primary == GuidanceAction.MOVE ? ID_B : null,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                20,
                List.of("CURRENT_SPOT_UNPRODUCTIVE"),
                "Any closed action pair is schema-legal.",
                List.of(committedMove()),
                0.5
        );
    }

    static DeliveredDecision delivered() {
        return new DeliveredDecision(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                GuidanceAction.STAY,
                GuidanceAction.CHANGE_RETRIEVE,
                null,
                LureFamily.JERKBAIT,
                PresentationTechnique.TWITCH_PAUSE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                15,
                List.of("CONTINUE_CURRENT_SPOT"),
                "Stay and slow the retrieve.",
                List.of(new HorizonStep(1, GuidanceAction.STAY, ID_B, 15, true)),
                false,
                null,
                null
        );
    }

    static SafetyVerdict safetyOk() {
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                SafetyVerdictLevel.OK,
                List.of(),
                List.of(),
                List.of(GuidanceAction.values()),
                null
        );
    }

    static SafetyVerdict safetyBlock() {
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                SafetyVerdictLevel.BLOCK,
                List.of("Severe thunderstorm on the water"),
                List.of(SafetyConstraintCode.THUNDERSTORM),
                List.of(GuidanceAction.RETURN),
                GuidanceAction.RETURN
        );
    }

    static DecisionValidationResult validation() {
        return new DecisionValidationResult(GuidanceSchemaVersion.VALUE, true, List.of());
    }

    static SessionEvent sessionEvent() {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                SessionEventType.NO_BITE,
                AT,
                EventSource.DERIVED,
                "no-bite-1",
                Map.of("noBiteMinutes", 31),
                null
        );
    }

    static SessionEvent biteEvent() {
        return new SessionEvent(
                GuidanceSchemaVersion.VALUE,
                SessionEventType.BITE,
                AT,
                EventSource.CLIENT,
                "bite-1",
                Map.of(),
                ID_A
        );
    }

    static TriggerRoutingDecision routingDecision() {
        return new TriggerRoutingDecision(
                GuidanceTrigger.SAFETY_STATE_CHANGED,
                List.of(GuidanceTrigger.NO_BITE_THRESHOLD, GuidanceTrigger.REPEATED_BITE_PATTERN),
                List.of("SAFETY_THUNDERSTORM", "NO_BITE_THRESHOLD_CROSSED")
        );
    }

    static ToolRequestEnvelope toolRequest() {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("latitudeWgs84", 44.75);
        args.put("longitudeWgs84", -78.92);
        args.put("radiusMeters", 400);
        args.put("targetSpecies", "SMALLMOUTH_BASS");
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_NEARBY_WAYPOINTS, args, AT);
    }

    static ToolResultEnvelope toolResult() {
        ObjectNode data = GuidanceContracts.mapper().createObjectNode();
        data.put("count", 2);
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE, ToolResultStatus.OK, AT, "internal-waypoint-service", data, null, null, null
        );
    }

    static ToolCallRecord toolCall() {
        return new ToolCallRecord(GuidanceSchemaVersion.VALUE, toolRequest(), toolResult(), 12, AT);
    }

    static AgentRunRequest runRequest() {
        return new AgentRunRequest(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                ID_B,
                GuidanceTrigger.USER_REQUEST,
                state(),
                context(),
                List.of("mem-1"),
                null, null, null, null, null, null, null, null, null
        );
    }

    static EvalComponentVersions evalComponentVersions() {
        return new EvalComponentVersions(
                "prompt-1", "deterministic", "fixture", "1", "tools-v1", "context-v1",
                "memory-v1", "safety-v1", "validator-v1",
                "v1", "1", null
        );
    }

    static EvalCoverage evalCoverage() {
        return new EvalCoverage(3, 1, 4, 2, 0.75);
    }

    static EvalRun evalRun() {
        return new EvalRun(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                "platform/safety-block",
                EvalSuiteKind.PLATFORM_REGRESSION,
                ReplayMode.FROZEN_REPLAY,
                List.of(RecomputedComponent.PROMPT, RecomputedComponent.MODEL),
                evalComponentVersions(),
                "pricing-2026-09",
                evalCoverage(),
                "scenario-safety-block",
                7L,
                null,
                List.of("safety", "block"),
                AT,
                AT
        );
    }

    static EvalCaseResult evalCaseResult() {
        return new EvalCaseResult(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                "platform/safety-block/return",
                EvalCaseResultStatus.PASS,
                List.of(GuidanceAction.RETURN),
                List.of(GuidanceAction.MOVE, GuidanceAction.STAY),
                List.of("BLOCK_REQUIRES_RETURN"),
                delivered(),
                GuidanceAction.RETURN,
                GuidanceAction.RETURN,
                OutcomeKind.NONE,
                null,
                null,
                "scenario-safety-block",
                7L
        );
    }

    static OnlineGuidanceMetrics onlineGuidanceMetrics() {
        return new OnlineGuidanceMetrics(
                GuidanceSchemaVersion.VALUE,
                AT,
                AT,
                AttributionDimension.LOCATION,
                2,
                1,
                3,
                1,
                0,
                4,
                0.5,
                0.4,
                0.2,
                0.1,
                0.05,
                0.01,
                0.1,
                0.0,
                0.0,
                3600L,
                2.0,
                3.0
        );
    }

    static UsageTelemetry usageTelemetryUnknown() {
        return new UsageTelemetry(
                GuidanceSchemaVersion.VALUE,
                "deterministic",
                "fixture",
                "1",
                "prompt-1",
                null,
                null,
                null,
                null,
                null
        );
    }

    static FrozenAgentRunSnapshot frozenSnapshot() {
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                ID_B,
                GuidanceTrigger.USER_REQUEST,
                state(),
                context(),
                RetrievedMemory.empty(),
                List.of(toolCall()),
                AT,
                evalComponentVersions()
        );
    }

    static AgentRunResult runResult() {
        return new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                ID_A,
                AgentRunStatus.COMPLETED,
                runRequest(),
                List.of(toolCall()),
                candidate(),
                validation(),
                delivered()
        );
    }

    static JsonNode json(Object value) {
        return GuidanceContracts.mapper().valueToTree(value);
    }
}
