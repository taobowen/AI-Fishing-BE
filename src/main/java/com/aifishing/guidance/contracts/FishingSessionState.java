package com.aifishing.guidance.contracts;

import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FishingSessionState(
        String schemaVersion,
        Session session,
        Position position,
        Boat boat,
        Fishing fishing,
        Environment environment,
        Recent recent,
        Performance performance,
        Plan plan,
        OpportunityRevisit opportunityRevisit
) {
    public FishingSessionState {
        opportunityRevisit = opportunityRevisit == null ? OpportunityRevisit.empty() : opportunityRevisit;
    }

    @JsonCreator(mode = JsonCreator.Mode.DISABLED)
    public FishingSessionState(
            String schemaVersion,
            Session session,
            Position position,
            Boat boat,
            Fishing fishing,
            Environment environment,
            Recent recent,
            Performance performance,
            Plan plan
    ) {
        this(
                schemaVersion,
                session,
                position,
                boat,
                fishing,
                environment,
                recent,
                performance,
                plan,
                OpportunityRevisit.empty()
        );
    }

    public record Session(
            UUID sessionId,
            UUID userId,
            FishingSessionStatus status,
            Instant startedAt,
            FishSpecies targetSpecies,
            Integer remainingTimeMinutes
    ) {
    }

    public record Position(
            double latitudeWgs84,
            double longitudeWgs84,
            Double gpsAccuracyM,
            Double speedMps,
            Double headingDegrees
    ) {
    }

    public record Boat(
            BoatType boatType,
            PropulsionType propulsionType,
            Double cruiseSpeedMps,
            Double remainingRangeMeters,
            Double returnReserveMeters
    ) {
    }

    public record Fishing(
            UUID currentTripWaypointId,
            UUID currentSessionWaypointProgressId,
            FeatureType structureType,
            Double depthMinM,
            Double depthMaxM,
            LureFamily lureFamily,
            PresentationTechnique presentation,
            RetrieveStyle retrieveStyle,
            Integer timeAtWaypointMinutes,
            FishingActivityState activityState,
            Instant activityStateSince,
            ActivityStateSource activityStateSource,
            Integer activeFishingEffortMinutes,
            Integer noBiteMinutes,
            UUID adHocFishingStopId,
            Instant adHocStartedAt,
            UUID fishingTargetId,
            UUID physicalZoneId,
            UUID lakeFeatureId
    ) {
        public Fishing {
            if (activityState == null) {
                activityState = FishingActivityState.UNKNOWN;
            }
            if (activityStateSource == null) {
                activityStateSource = ActivityStateSource.UNKNOWN;
            }
        }

        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public Fishing(
                UUID currentTripWaypointId,
                UUID currentSessionWaypointProgressId,
                FeatureType structureType,
                Double depthMinM,
                Double depthMaxM,
                LureFamily lureFamily,
                PresentationTechnique presentation,
                RetrieveStyle retrieveStyle,
                Integer timeAtWaypointMinutes,
                FishingActivityState activityState,
                Instant activityStateSince,
                ActivityStateSource activityStateSource,
                Integer activeFishingEffortMinutes,
                Integer noBiteMinutes
        ) {
            this(
                    currentTripWaypointId,
                    currentSessionWaypointProgressId,
                    structureType,
                    depthMinM,
                    depthMaxM,
                    lureFamily,
                    presentation,
                    retrieveStyle,
                    timeAtWaypointMinutes,
                    activityState,
                    activityStateSince,
                    activityStateSource,
                    activeFishingEffortMinutes,
                    noBiteMinutes,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    public record Environment(
            WeatherCondition weather,
            Double windSpeedKph,
            CompassDirection windDirection,
            Double pressureHpa,
            Double temperatureC,
            Instant weatherObservedAt,
            Integer weatherAgeMinutes
    ) {
    }

    public record Recent(
            List<UUID> moveIds,
            List<UUID> lureChangeIds,
            List<UUID> catchEventIds,
            List<UUID> adviceIds,
            List<UUID> rejectedAdviceIds,
            List<String> summaries
    ) {
    }

    public record Performance(
            int catchesThisSession,
            Double sessionCpue,
            Integer timeSinceLastCatchMinutes,
            Integer currentWaypointCatchCount
    ) {
    }

    public record Plan(
            UUID originalTripPlanId,
            Integer currentGuidancePlanVersion,
            Integer currentStep,
            List<HorizonStep> shortHorizonSteps,
            List<OriginalPlanStep> originalPlanSteps,
            UUID activeGuidanceTargetTripWaypointId
    ) {
        public Plan {
            originalPlanSteps = originalPlanSteps == null ? List.of() : List.copyOf(originalPlanSteps);
        }

        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public Plan(
                UUID originalTripPlanId,
                Integer currentGuidancePlanVersion,
                Integer currentStep,
                List<HorizonStep> shortHorizonSteps
        ) {
            this(originalTripPlanId, currentGuidancePlanVersion, currentStep, shortHorizonSteps, List.of(), null);
        }

        @JsonCreator(mode = JsonCreator.Mode.DISABLED)
        public Plan(
                UUID originalTripPlanId,
                Integer currentGuidancePlanVersion,
                Integer currentStep,
                List<HorizonStep> shortHorizonSteps,
                List<OriginalPlanStep> originalPlanSteps
        ) {
            this(
                    originalTripPlanId,
                    currentGuidancePlanVersion,
                    currentStep,
                    shortHorizonSteps,
                    originalPlanSteps,
                    null
            );
        }
    }

    /**
     * Bounded package-member cooldown slice (cap {@link #MAX_ENTRIES}), not an
     * actual-member consumption ledger. A fished visit cools that visit's stored
     * {@code packageMemberIds} (POINT/PATH: the atomic identity of the visit).
     * Disjoint packages on the same {@code physicalZoneId} stay eligible.
     * Task A writes this slice from visit leave + stored package members.
     */
    public record OpportunityRevisit(
            List<PackageCooldown> packageCooldowns,
            List<UUID> lastMoveTargetTripWaypointIds
    ) {
        public static final int MAX_ENTRIES = 8;

        public OpportunityRevisit {
            packageCooldowns = bound(packageCooldowns);
            lastMoveTargetTripWaypointIds = bound(lastMoveTargetTripWaypointIds);
        }

        public static OpportunityRevisit empty() {
            return new OpportunityRevisit(List.of(), List.of());
        }

        private static <T> List<T> bound(List<T> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            int size = values.size();
            if (size <= MAX_ENTRIES) {
                return List.copyOf(values);
            }
            return List.copyOf(values.subList(size - MAX_ENTRIES, size));
        }
    }

    /**
     * Package-member identities cooling after leave. Not a record of which
     * members were actually fished.
     */
    public record PackageCooldown(
            List<UUID> packageMemberIds,
            Instant cooldownUntil,
            UUID physicalZoneId
    ) {
        public PackageCooldown {
            packageMemberIds = packageMemberIds == null ? List.of() : List.copyOf(packageMemberIds);
        }
    }
}
