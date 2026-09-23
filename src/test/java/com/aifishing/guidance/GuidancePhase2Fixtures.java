package com.aifishing.guidance;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.CompassDirection;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.RetrieveStyle;
import com.aifishing.guidance.contracts.SafetyConstraintCode;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.SafetyVerdictLevel;
import com.aifishing.guidance.contracts.WeatherCondition;
import com.aifishing.lake.processing.dto.FeatureType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GuidancePhase2Fixtures {

    public static final Instant AT = Instant.parse("2026-09-16T13:33:00Z");
    public static final UUID SESSION_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    public static final UUID USER_ID = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID TRIP_WAYPOINT = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID PROGRESS_ID = UUID.fromString("7ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID OTHER_WAYPOINT = UUID.fromString("8ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final UUID SPOT_1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000001");
    public static final UUID SPOT_2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
    public static final UUID SPOT_3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
    public static final UUID DECISION_ID = SESSION_ID;

    private GuidancePhase2Fixtures() {
    }

    public static FishingSessionState safeState() {
        return state(WeatherCondition.CLOUDY, 16.0, 4.5, 12_000.0, 1_500.0, 90, TRIP_WAYPOINT, List.of(
                new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true),
                new HorizonStep(2, GuidanceAction.STAY, null, 20, false)
        ));
    }

    public static FishingSessionState state(
            WeatherCondition weather,
            Double windSpeedKph,
            Double gpsAccuracyM,
            Double remainingRangeMeters,
            Double returnReserveMeters,
            Integer remainingTimeMinutes,
            UUID currentTripWaypointId,
            List<HorizonStep> shortHorizonSteps
    ) {
        return state(
                weather,
                windSpeedKph,
                gpsAccuracyM,
                remainingRangeMeters,
                returnReserveMeters,
                remainingTimeMinutes,
                currentTripWaypointId,
                shortHorizonSteps,
                List.of()
        );
    }

    public static FishingSessionState state(
            WeatherCondition weather,
            Double windSpeedKph,
            Double gpsAccuracyM,
            Double remainingRangeMeters,
            Double returnReserveMeters,
            Integer remainingTimeMinutes,
            UUID currentTripWaypointId,
            List<HorizonStep> shortHorizonSteps,
            List<OriginalPlanStep> originalPlanSteps
    ) {
        return new FishingSessionState(
                GuidanceSchemaVersion.VALUE,
                new FishingSessionState.Session(
                        SESSION_ID, USER_ID, FishingSessionStatus.ACTIVE, AT, FishSpecies.SMALLMOUTH_BASS,
                        remainingTimeMinutes
                ),
                new FishingSessionState.Position(44.75, -78.92, gpsAccuracyM, 1.2, 210.0),
                new FishingSessionState.Boat(
                        BoatType.FISHING_BOAT, PropulsionType.GAS_OUTBOARD, 8.0,
                        remainingRangeMeters, returnReserveMeters
                ),
                new FishingSessionState.Fishing(
                        currentTripWaypointId, PROGRESS_ID, FeatureType.POINT, 3.0, 4.5, LureFamily.JERKBAIT,
                        PresentationTechnique.TWITCH_PAUSE, RetrieveStyle.SLOW, 31,
                        FishingActivityState.FISHING, AT, ActivityStateSource.PROGRESS, 31, 31
                ),
                new FishingSessionState.Environment(
                        weather, windSpeedKph, CompassDirection.W, 1012.0, 18.0, AT, 4
                ),
                new FishingSessionState.Recent(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new FishingSessionState.Performance(0, 0.0, 31, 0),
                new FishingSessionState.Plan(SESSION_ID, 1, 1, shortHorizonSteps, originalPlanSteps)
        );
    }

    public static CandidateDecision moveCandidate() {
        return candidate(
                GuidanceAction.MOVE,
                GuidanceAction.CHANGE_LURE,
                TRIP_WAYPOINT,
                List.of(new HorizonStep(1, GuidanceAction.MOVE, TRIP_WAYPOINT, null, true)),
                20
        );
    }

    public static CandidateDecision retrieveCandidate() {
        return candidate(
                GuidanceAction.CHANGE_RETRIEVE,
                null,
                null,
                List.of(new HorizonStep(1, GuidanceAction.CHANGE_RETRIEVE, null, 15, true)),
                15
        );
    }

    public static CandidateDecision candidate(
            GuidanceAction primary,
            GuidanceAction secondary,
            UUID targetTripWaypointId,
            List<HorizonStep> horizon,
            int reevaluateAfterMinutes
    ) {
        return new CandidateDecision(
                GuidanceSchemaVersion.VALUE,
                DECISION_ID,
                primary,
                secondary,
                targetTripWaypointId,
                LureFamily.TUBE,
                PresentationTechnique.STEADY_RETRIEVE,
                3.0,
                4.5,
                RetrieveStyle.SLOW,
                reevaluateAfterMinutes,
                List.of("CURRENT_SPOT_UNPRODUCTIVE"),
                "Focused Phase 2 validator fixture.",
                horizon == null ? List.of() : new ArrayList<>(horizon),
                0.76
        );
    }

    public static SafetyVerdict safetyOk() {
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                SafetyVerdictLevel.OK,
                List.of(),
                List.of(),
                List.of(GuidanceAction.values()),
                null
        );
    }

    public static SafetyVerdict safetyWarning(SafetyConstraintCode code, List<GuidanceAction> allowed) {
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                SafetyVerdictLevel.WARNING,
                List.of("warning"),
                List.of(code),
                allowed,
                null
        );
    }

    public static SafetyVerdict safetyBlock(SafetyConstraintCode code, GuidanceAction prescribed) {
        return new SafetyVerdict(
                GuidanceSchemaVersion.VALUE,
                SafetyVerdictLevel.BLOCK,
                List.of("blocked"),
                List.of(code),
                List.of(prescribed),
                prescribed
        );
    }
}
