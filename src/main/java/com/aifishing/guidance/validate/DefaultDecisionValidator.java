package com.aifishing.guidance.validate;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.DecisionValidationResult;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.HorizonStep;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.guidance.contracts.SafetyConstraintCode;
import com.aifishing.guidance.contracts.SafetyVerdict;
import com.aifishing.guidance.contracts.SafetyVerdictLevel;
import com.aifishing.guidance.contracts.ValidationIssue;
import com.aifishing.guidance.revisit.OpportunityRevisitSupport;
import com.aifishing.guidance.spi.DecisionValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Action-aware checks. Weather/wind is read only from
 * {@link SafetyVerdict#constraintCodes()} and {@link SafetyVerdict#allowedActions()};
 * this class must not recompute Open-Meteo or {@code BoatWeatherPenalty}.
 */
@Component
public class DefaultDecisionValidator implements DecisionValidator {

    private static final Set<SafetyConstraintCode> WEATHER_CODES = EnumSet.of(
            SafetyConstraintCode.WIND_UNSAFE,
            SafetyConstraintCode.THUNDERSTORM,
            SafetyConstraintCode.LIGHTNING
    );
    private static final Set<String> ELIGIBLE_ORIGINAL_MOVE_STATUSES = Set.of(
            WaypointProgressStatus.UPCOMING.name(),
            WaypointProgressStatus.NAVIGATING.name(),
            WaypointProgressStatus.ARRIVED.name(),
            WaypointProgressStatus.FISHING.name()
    );

    private static boolean compatiblePair(GuidanceAction primary, GuidanceAction secondary) {
        return (primary == GuidanceAction.MOVE && secondary == GuidanceAction.CHANGE_LURE)
                || (primary == GuidanceAction.STAY && secondary == GuidanceAction.CHANGE_RETRIEVE)
                || (primary == GuidanceAction.CHANGE_DEPTH && secondary == GuidanceAction.CHANGE_RETRIEVE);
    }

    private final SessionProperties sessionProperties;
    private final OpportunityRevisitSupport opportunityRevisitSupport;

    public DefaultDecisionValidator(SessionProperties sessionProperties) {
        this(sessionProperties, new OpportunityRevisitSupport(new GuidanceProperties(), sessionProperties, Clock.systemUTC()));
    }

    @Autowired
    public DefaultDecisionValidator(
            SessionProperties sessionProperties,
            OpportunityRevisitSupport opportunityRevisitSupport
    ) {
        this.sessionProperties = sessionProperties;
        this.opportunityRevisitSupport = opportunityRevisitSupport;
    }

    @Override
    public DecisionValidationResult validate(
            CandidateDecision candidate,
            FishingSessionState state,
            SafetyVerdict safetyVerdict
    ) {
        if (candidate == null || state == null || safetyVerdict == null) {
            throw new IllegalArgumentException("candidate, state, and safetyVerdict are required");
        }
        List<ValidationIssue> issues = new ArrayList<>();
        recordIssue(issues, DecisionValidationCheck.SCHEMA_INVALID, schema(candidate));
        recordIssue(issues, DecisionValidationCheck.ACTION_INCOMPATIBLE, actionCompatible(candidate, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.HORIZON_COMMIT_INVALID, horizon(candidate));
        recordIssue(issues, DecisionValidationCheck.WAYPOINT_NOT_FOUND, waypointFound(candidate, state));
        recordIssue(issues, DecisionValidationCheck.WAYPOINT_NOT_NAVIGABLE, waypointNavigable(candidate, state));
        recordIssue(issues, DecisionValidationCheck.WAYPOINT_ID_INCONSISTENT, waypointIdsConsistent(candidate, state));
        recordIssue(issues, DecisionValidationCheck.WAYPOINT_NOT_IN_SESSION, waypointInSession(candidate, state));
        recordIssue(issues, DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE, waypointOnLake(candidate, state));
        recordIssue(issues, DecisionValidationCheck.GPS_ACCURACY_UNACCEPTABLE, gps(candidate, state, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.INSUFFICIENT_REMAINING_RANGE, remainingRange(candidate, state, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.INSUFFICIENT_RETURN_RESERVE, returnReserve(candidate, state, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.INSUFFICIENT_REMAINING_TIME, remainingTime(candidate, state));
        recordIssue(issues, DecisionValidationCheck.WEATHER_UNSAFE, weather(candidate, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.NO_FISHING_ZONE, noFishingZone(candidate, safetyVerdict));
        recordIssue(issues, DecisionValidationCheck.OPPORTUNITY_IN_COOLDOWN, opportunityInCooldown(candidate, state));
        recordIssue(issues, DecisionValidationCheck.MOVE_OSCILLATION, moveOscillation(candidate, state));
        return new DecisionValidationResult(GuidanceSchemaVersion.VALUE, issues.isEmpty(), List.copyOf(issues));
    }

    private static boolean navigationSensitive(GuidanceAction action) {
        return action == GuidanceAction.MOVE;
    }

    private Outcome schema(CandidateDecision candidate) {
        if (!GuidanceSchemaVersion.VALUE.equals(candidate.schemaVersion())) {
            return Outcome.fail("schemaVersion must be " + GuidanceSchemaVersion.VALUE);
        }
        if (candidate.primaryAction() == null) {
            return Outcome.fail("primaryAction is required");
        }
        if (candidate.shortExplanation() == null || candidate.shortExplanation().isBlank()) {
            return Outcome.fail("shortExplanation is required");
        }
        if (candidate.modelConfidence() < 0 || candidate.modelConfidence() > 1) {
            return Outcome.fail("modelConfidence must be within [0, 1]");
        }
        if (candidate.depthMinM() != null && candidate.depthMaxM() != null
                && candidate.depthMinM() > candidate.depthMaxM()) {
            return Outcome.fail("depthMinM must be less than or equal to depthMaxM");
        }
        if (candidate.primaryAction() == GuidanceAction.CHANGE_RETRIEVE && candidate.retrieveStyle() == null) {
            return Outcome.fail("CHANGE_RETRIEVE requires retrieveStyle");
        }
        if (candidate.primaryAction() == GuidanceAction.CHANGE_LURE && candidate.suggestedLure() == null) {
            return Outcome.fail("CHANGE_LURE requires suggestedLure");
        }
        if (candidate.primaryAction() == GuidanceAction.CHANGE_DEPTH
                && (candidate.depthMinM() == null || candidate.depthMaxM() == null)) {
            return Outcome.fail("CHANGE_DEPTH requires depthMinM and depthMaxM");
        }
        JsonNode tree = GuidanceContracts.mapper().valueToTree(candidate);
        Set<ValidationMessage> schemaIssues = GuidanceContracts.schema("CandidateDecision").validate(tree);
        if (!schemaIssues.isEmpty()) {
            return Outcome.fail("candidate does not match CandidateDecision schema");
        }
        return Outcome.pass();
    }

    private Outcome actionCompatible(CandidateDecision candidate, SafetyVerdict verdict) {
        GuidanceAction primary = candidate.primaryAction();
        GuidanceAction secondary = candidate.secondaryAction();
        if (primary == null) {
            return Outcome.fail("primaryAction is required");
        }
        if (secondary != null) {
            if (secondary == primary) {
                return Outcome.fail("secondaryAction must differ from primaryAction");
            }
            if (primary == GuidanceAction.RETURN) {
                return Outcome.fail("RETURN cannot be paired with a secondary action");
            }
            if (!compatiblePair(primary, secondary)) {
                return Outcome.fail(primary + " cannot be paired with " + secondary);
            }
        }
        if (!actionAllowed(primary, verdict) || (secondary != null && !actionAllowed(secondary, verdict))) {
            if (hasWeatherConstraint(verdict)) {
                return Outcome.pass();
            }
            return Outcome.fail("action is not in safety allowedActions");
        }
        return Outcome.pass();
    }

    private Outcome horizon(CandidateDecision candidate) {
        List<HorizonStep> horizon = candidate.proposedHorizon();
        if (horizon == null || horizon.isEmpty()) {
            return Outcome.fail("proposedHorizon must contain a committed next step");
        }
        List<HorizonStep> committed = horizon.stream().filter(HorizonStep::committed).toList();
        if (committed.isEmpty()) {
            return Outcome.fail("proposedHorizon must commit exactly one next step");
        }
        if (committed.size() > 1) {
            return Outcome.fail("proposedHorizon must commit only the next step");
        }
        HorizonStep next = committed.getFirst();
        if (next.step() != 1) {
            return Outcome.fail("committed step must be step 1");
        }
        if (candidate.primaryAction() != null && next.type() != candidate.primaryAction()) {
            return Outcome.fail("committed step type must match primaryAction");
        }
        return Outcome.pass();
    }

    private Outcome waypointFound(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (candidate.targetTripWaypointId() == null) {
            return Outcome.fail("MOVE requires targetTripWaypointId");
        }
        return Outcome.pass();
    }

    private Outcome waypointNavigable(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (candidate.targetTripWaypointId() == null) {
            return Outcome.notApplicable();
        }
        Double remaining = range(state);
        if (remaining == null) {
            return Outcome.unknown("remaining range is required to judge navigability");
        }
        if (remaining <= 0) {
            return Outcome.fail("remaining range is exhausted; waypoint is not navigable");
        }
        return Outcome.pass();
    }

    private Outcome waypointIdsConsistent(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        UUID target = candidate.targetTripWaypointId();
        if (target == null) {
            return Outcome.notApplicable();
        }
        FishingSessionState.Fishing fishing = state.fishing();
        UUID tripId = fishing == null ? null : fishing.currentTripWaypointId();
        UUID progressId = fishing == null ? null : fishing.currentSessionWaypointProgressId();
        if (progressId != null && target.equals(progressId) && (tripId == null || !target.equals(tripId))) {
            return Outcome.fail("targetTripWaypointId matches sessionWaypointProgressId, not tripWaypointId");
        }
        HorizonStep committed = committedStep(candidate);
        if (committed != null && committed.type() == GuidanceAction.MOVE) {
            if (committed.tripWaypointId() == null) {
                return Outcome.fail("committed MOVE step is missing tripWaypointId");
            }
            if (!target.equals(committed.tripWaypointId())) {
                return Outcome.fail("targetTripWaypointId does not match the committed horizon waypoint");
            }
        }
        UUID current = fishing == null ? null : fishing.currentTripWaypointId();
        if (current != null
                && !target.equals(current)
                && opportunityRevisitSupport.stillAtCurrent(state)
                && opportunityRevisitSupport.samePhysicalIdentity(target, state)) {
            return Outcome.fail("MOVE target is the current physical identity");
        }
        return Outcome.pass();
    }

    private Outcome waypointInSession(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        UUID target = candidate.targetTripWaypointId();
        if (target == null) {
            return Outcome.notApplicable();
        }
        Set<UUID> known = sessionTripWaypointIds(state);
        if (known.isEmpty()) {
            return Outcome.unknown("session has no trip waypoint ids to compare");
        }
        if (!known.contains(target)) {
            return Outcome.fail("targetTripWaypointId is not part of the current session");
        }
        return Outcome.pass();
    }

    private Outcome waypointOnLake(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        UUID target = candidate.targetTripWaypointId();
        if (target == null) {
            return Outcome.notApplicable();
        }
        Set<UUID> known = sessionTripWaypointIds(state);
        if (known.isEmpty()) {
            return Outcome.unknown("session has no lake waypoint catalog");
        }
        if (!known.contains(target)) {
            return Outcome.fail("targetTripWaypointId is not a known waypoint on this lake session");
        }
        return Outcome.pass();
    }

    private Outcome gps(CandidateDecision candidate, FishingSessionState state, SafetyVerdict verdict) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (codes(verdict).contains(SafetyConstraintCode.GPS_ACCURACY_UNACCEPTABLE)) {
            return Outcome.fail("safety verdict reports GPS accuracy is unacceptable");
        }
        Double accuracyM = state.position() == null ? null : state.position().gpsAccuracyM();
        if (accuracyM == null) {
            return Outcome.unknown("gpsAccuracyM is required for MOVE");
        }
        double maxAccuracyM = sessionProperties.getLocation().getMaxAccuracyM();
        if (accuracyM > maxAccuracyM) {
            return Outcome.fail("GPS accuracy " + accuracyM + " m exceeds " + maxAccuracyM + " m");
        }
        return Outcome.pass();
    }

    private Outcome remainingRange(CandidateDecision candidate, FishingSessionState state, SafetyVerdict verdict) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (codes(verdict).contains(SafetyConstraintCode.RANGE_INSUFFICIENT)) {
            return Outcome.fail("safety verdict reports remaining range is insufficient");
        }
        Double remaining = range(state);
        if (remaining == null) {
            return Outcome.unknown("remainingRangeMeters is required for MOVE");
        }
        Double reserve = reserve(state);
        if (remaining <= 0 || (reserve != null && remaining <= reserve)) {
            return Outcome.fail("remaining range is insufficient for an outward MOVE");
        }
        return Outcome.pass();
    }

    private Outcome returnReserve(CandidateDecision candidate, FishingSessionState state, SafetyVerdict verdict) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (codes(verdict).contains(SafetyConstraintCode.RETURN_RESERVE_INSUFFICIENT)) {
            return Outcome.fail("safety verdict reports reserve distance is insufficient");
        }
        Double reserve = reserve(state);
        if (reserve == null) {
            return Outcome.unknown("returnReserveMeters is required for MOVE");
        }
        Double remaining = range(state);
        if (remaining != null && remaining <= reserve) {
            return Outcome.fail("remaining range does not cover the reserve distance");
        }
        return Outcome.pass();
    }

    private Outcome remainingTime(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        Integer remaining = state.session() == null ? null : state.session().remainingTimeMinutes();
        if (remaining == null) {
            return Outcome.unknown("remainingTimeMinutes is required for MOVE");
        }
        if (remaining <= 0) {
            return Outcome.fail("no remaining session time for MOVE");
        }
        if (remaining < candidate.reevaluateAfterMinutes()) {
            return Outcome.fail("remaining time is shorter than reevaluateAfterMinutes");
        }
        return Outcome.pass();
    }

    private Outcome weather(CandidateDecision candidate, SafetyVerdict verdict) {
        if (!hasWeatherConstraint(verdict)) {
            return Outcome.pass();
        }
        GuidanceAction primary = candidate.primaryAction();
        GuidanceAction secondary = candidate.secondaryAction();
        if (!actionAllowed(primary, verdict) || (secondary != null && !actionAllowed(secondary, verdict))) {
            return Outcome.fail("action is not allowed under the safety weather/wind verdict");
        }
        return Outcome.pass();
    }

    private Outcome noFishingZone(CandidateDecision candidate, SafetyVerdict verdict) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        if (codes(verdict).contains(SafetyConstraintCode.NO_FISHING_ZONE)) {
            return Outcome.fail("safety verdict reports a no-fishing zone constraint");
        }
        return Outcome.pass();
    }

    private Outcome opportunityInCooldown(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        UUID target = candidate.targetTripWaypointId();
        if (target == null) {
            return Outcome.notApplicable();
        }
        if (opportunityRevisitSupport.samePhysicalIdentity(target, state)
                && opportunityRevisitSupport.stillAtCurrent(state)) {
            return Outcome.notApplicable();
        }
        List<UUID> members = OpportunityRevisitSupport.packageMemberIds(
                OpportunityRevisitSupport.originalStep(state, target), target);
        if (!opportunityRevisitSupport.entirePackageCooling(members, state.opportunityRevisit())) {
            return Outcome.pass();
        }
        return Outcome.fail("target package is still in cooldown after the last fished visit");
    }

    private Outcome moveOscillation(CandidateDecision candidate, FishingSessionState state) {
        if (!navigationSensitive(candidate.primaryAction())) {
            return Outcome.notApplicable();
        }
        UUID target = candidate.targetTripWaypointId();
        if (target == null) {
            return Outcome.notApplicable();
        }
        if (opportunityRevisitSupport.samePhysicalIdentity(target, state)
                && opportunityRevisitSupport.stillAtCurrent(state)) {
            return Outcome.notApplicable();
        }
        if (!opportunityRevisitSupport.oscillatingMove(target, state.opportunityRevisit())) {
            return Outcome.pass();
        }
        if (opportunityRevisitSupport.materialEligibilityChange(candidate, state, target)) {
            return Outcome.pass();
        }
        return Outcome.fail("rapid MOVE A→B→A without a material eligibility change");
    }

    private static void recordIssue(List<ValidationIssue> issues, DecisionValidationCheck check, Outcome outcome) {
        if (outcome.status == CheckStatus.FAIL) {
            issues.add(new ValidationIssue(check, outcome.message));
            return;
        }
        if (outcome.status == CheckStatus.UNKNOWN) {
            DecisionValidationCheck mapped = check == DecisionValidationCheck.WAYPOINT_NOT_NAVIGABLE
                    || check == DecisionValidationCheck.WAYPOINT_NOT_ON_LAKE
                    ? check
                    : DecisionValidationCheck.REQUIRED_DATA_MISSING;
            issues.add(new ValidationIssue(mapped, outcome.message));
        }
    }

    private static boolean hasWeatherConstraint(SafetyVerdict verdict) {
        Set<SafetyConstraintCode> present = codes(verdict);
        for (SafetyConstraintCode code : WEATHER_CODES) {
            if (present.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean actionAllowed(GuidanceAction action, SafetyVerdict verdict) {
        if (action == null) {
            return false;
        }
        List<GuidanceAction> allowed = verdict.allowedActions();
        if (allowed == null || allowed.isEmpty()) {
            return verdict.level() == SafetyVerdictLevel.OK;
        }
        return allowed.contains(action);
    }

    private static Set<SafetyConstraintCode> codes(SafetyVerdict verdict) {
        return verdict.constraintCodes() == null
                ? Set.of()
                : new LinkedHashSet<>(verdict.constraintCodes());
    }

    private static Double range(FishingSessionState state) {
        return state.boat() == null ? null : state.boat().remainingRangeMeters();
    }

    private static Double reserve(FishingSessionState state) {
        return state.boat() == null ? null : state.boat().returnReserveMeters();
    }

    private static HorizonStep committedStep(CandidateDecision candidate) {
        if (candidate.proposedHorizon() == null) {
            return null;
        }
        return candidate.proposedHorizon().stream()
                .filter(HorizonStep::committed)
                .findFirst()
                .orElse(null);
    }

    private static Set<UUID> sessionTripWaypointIds(FishingSessionState state) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (state.fishing() != null && state.fishing().currentTripWaypointId() != null) {
            ids.add(state.fishing().currentTripWaypointId());
        }
        if (state.plan() != null && state.plan().shortHorizonSteps() != null) {
            for (HorizonStep step : state.plan().shortHorizonSteps()) {
                if (step != null && step.tripWaypointId() != null) {
                    ids.add(step.tripWaypointId());
                }
            }
        }
        if (state.plan() != null && state.plan().originalPlanSteps() != null) {
            for (OriginalPlanStep step : state.plan().originalPlanSteps()) {
                if (step != null
                        && step.tripWaypointId() != null
                        && ELIGIBLE_ORIGINAL_MOVE_STATUSES.contains(step.progressStatus())) {
                    ids.add(step.tripWaypointId());
                }
            }
        }
        return ids;
    }

    private record Outcome(CheckStatus status, String message) {
        private static Outcome pass() {
            return new Outcome(CheckStatus.PASS, null);
        }

        private static Outcome notApplicable() {
            return new Outcome(CheckStatus.NOT_APPLICABLE, null);
        }

        private static Outcome fail(String message) {
            return new Outcome(CheckStatus.FAIL, Objects.requireNonNull(message));
        }

        private static Outcome unknown(String message) {
            return new Outcome(CheckStatus.UNKNOWN, Objects.requireNonNull(message));
        }
    }
}
