package com.aifishing.guidance.eval;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingAgentContext;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.RetrievedMemory;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic snapshots referenced by classpath eval fixtures.
 */
public final class EvalFixtures {

    public static final Instant CLOCK = Instant.parse("2026-09-16T13:33:00Z");
    public static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    public static final UUID USER_ID = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID RUN_SAFETY = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000001");
    public static final UUID RUN_STAY = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000002");
    public static final UUID RUN_POLICY = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000003");
    public static final UUID RUN_SHADOW = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000004");
    public static final UUID WAYPOINT = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID PROGRESS = UUID.fromString("7ba7b810-9dad-11d1-80b4-00c04fd430c8");

    private EvalFixtures() {
    }

    public static FrozenAgentRunSnapshot byRef(String snapshotRef) {
        if (snapshotRef == null || snapshotRef.isBlank()) {
            return null;
        }
        return switch (snapshotRef) {
            case "safety-block" -> safetyBlock();
            case "deterministic-stay" -> deterministicStay();
            case "policy-no-bite" -> policyNoBite();
            case "shadow-stay-fish-on" -> shadowStayFishOn();
            default -> throw new IllegalArgumentException("Unknown eval snapshotRef: " + snapshotRef);
        };
    }

    public static FrozenAgentRunSnapshot safetyBlock() {
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                RUN_SAFETY,
                SESSION_ID,
                GuidanceTrigger.SAFETY_STATE_CHANGED,
                state(WeatherCondition.THUNDERSTORM, 16.0),
                null,
                RetrievedMemory.empty(),
                List.of(),
                CLOCK,
                versions()
        );
    }

    public static FrozenAgentRunSnapshot deterministicStay() {
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                RUN_STAY,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                state(WeatherCondition.CLOUDY, 16.0),
                context(GuidanceTrigger.USER_REQUEST),
                RetrievedMemory.empty(),
                List.of(recordedNearby()),
                CLOCK,
                versions()
        );
    }

    public static FrozenAgentRunSnapshot policyNoBite() {
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                RUN_POLICY,
                SESSION_ID,
                GuidanceTrigger.NO_BITE_THRESHOLD,
                state(WeatherCondition.CLOUDY, 16.0),
                context(GuidanceTrigger.NO_BITE_THRESHOLD),
                RetrievedMemory.empty(),
                List.of(recordedNearby()),
                CLOCK,
                versions()
        );
    }

    public static FrozenAgentRunSnapshot shadowStayFishOn() {
        return new FrozenAgentRunSnapshot(
                GuidanceSchemaVersion.VALUE,
                RUN_SHADOW,
                SESSION_ID,
                GuidanceTrigger.USER_REQUEST,
                state(WeatherCondition.CLOUDY, 16.0),
                context(GuidanceTrigger.USER_REQUEST),
                RetrievedMemory.empty(),
                List.of(),
                CLOCK,
                versions()
        );
    }

    static FishingSessionState state(WeatherCondition weather, Double windSpeedKph) {
        return new FishingSessionState(
                GuidanceSchemaVersion.VALUE,
                new FishingSessionState.Session(
                        SESSION_ID, USER_ID, FishingSessionStatus.ACTIVE, CLOCK, FishSpecies.SMALLMOUTH_BASS, 90
                ),
                new FishingSessionState.Position(44.75, -78.92, 4.5, 1.2, 210.0),
                new FishingSessionState.Boat(
                        BoatType.FISHING_BOAT, PropulsionType.GAS_OUTBOARD, 8.0, 12_000.0, 1_500.0
                ),
                new FishingSessionState.Fishing(
                        WAYPOINT, PROGRESS, FeatureType.POINT, 3.0, 4.5, LureFamily.JERKBAIT,
                        PresentationTechnique.TWITCH_PAUSE, RetrieveStyle.SLOW, 31,
                        FishingActivityState.FISHING, CLOCK, ActivityStateSource.PROGRESS, 31, 31
                ),
                new FishingSessionState.Environment(
                        weather, windSpeedKph, CompassDirection.W, 1012.0, 18.0, CLOCK, 4
                ),
                new FishingSessionState.Recent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new FishingSessionState.Performance(0, 0.0, 31, 0),
                new FishingSessionState.Plan(SESSION_ID, 1, 1, List.of(
                        new HorizonStep(1, GuidanceAction.STAY, WAYPOINT, 20, true)
                ))
        );
    }

    static FishingAgentContext context(GuidanceTrigger trigger) {
        return new FishingAgentContext(
                GuidanceSchemaVersion.VALUE,
                trigger,
                FishSpecies.SMALLMOUTH_BASS,
                new FishingAgentContext.CurrentSituation(
                        WAYPOINT, PROGRESS, FeatureType.POINT, 31, 31, LureFamily.JERKBAIT, 3.0, 4.5
                ),
                new FishingAgentContext.ContextWeather(CompassDirection.W, 16.0, 4),
                List.of("No bites since arriving"),
                null,
                null,
                null
        );
    }

    static ToolCallRecord recordedNearby() {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("latitudeWgs84", 44.75);
        args.put("longitudeWgs84", -78.92);
        args.put("radiusMeters", 400);
        ObjectNode data = GuidanceContracts.mapper().createObjectNode();
        data.put("count", 2);
        return new ToolCallRecord(
                GuidanceSchemaVersion.VALUE,
                new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, ToolName.GET_NEARBY_WAYPOINTS, args, CLOCK),
                new ToolResultEnvelope(
                        GuidanceSchemaVersion.VALUE, ToolResultStatus.OK, CLOCK, "eval-recorded", data, null, null, null
                ),
                12,
                CLOCK
        );
    }

    static EvalComponentVersions versions() {
        return new EvalComponentVersions(
                "guidance-prompt-v1",
                "deterministic",
                "deterministic",
                "v1",
                "guidance-tools-v1",
                "guidance-context-v1",
                "memory-v1",
                "safety-v1",
                "validator-v1",
                "v1",
                "1",
                null
        );
    }
}
