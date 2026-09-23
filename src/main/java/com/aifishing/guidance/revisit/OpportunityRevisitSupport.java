package com.aifishing.guidance.revisit;

import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.CandidateDecision;
import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.contracts.FishingSessionState;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OriginalPlanStep;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.spatial.TargetKind;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Package-member POINT/PATH/ZONE cooldown and delivered-MOVE oscillation.
 * Not an actual-member consumption ledger.
 */
@Component
public class OpportunityRevisitSupport {

    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String COOLED = "COOLED";

    private final GuidanceProperties.OpportunityRevisit cfg;
    private final int meaningfulDwellSeconds;
    private final Clock clock;

    public OpportunityRevisitSupport(
            GuidanceProperties guidanceProperties,
            SessionProperties sessionProperties,
            Clock clock
    ) {
        this.cfg = guidanceProperties == null
                ? new GuidanceProperties().getOpportunityRevisit()
                : guidanceProperties.getOpportunityRevisit();
        this.meaningfulDwellSeconds = sessionProperties == null
                ? 120
                : Math.max(1, sessionProperties.getWaypoint().getFishingDwellSeconds());
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public FishingSessionState.OpportunityRevisit assemble(
            List<SessionWaypointProgress> progress,
            Map<UUID, TripWaypoint> waypoints,
            List<DeliveredDecision> delivered
    ) {
        List<FishingSessionState.PackageCooldown> cooled = new ArrayList<>();
        if (progress != null) {
            List<SessionWaypointProgress> left = progress.stream()
                    .filter(row -> row != null && row.getDepartedAt() != null && meaningfulEffort(row))
                    .sorted(Comparator.comparing(SessionWaypointProgress::getDepartedAt)
                            .thenComparing(SessionWaypointProgress::getSequence))
                    .toList();
            for (SessionWaypointProgress row : left) {
                TripWaypoint waypoint = waypoints == null ? null : waypoints.get(row.getTripWaypointId());
                List<UUID> members = packageMemberIds(waypoint, row.getTripWaypointId());
                Instant until = row.getDepartedAt().plus(Duration.ofMinutes(cooldownMinutes(visitKind(waypoint))));
                UUID zoneId = waypoint == null ? null : waypoint.getZoneId();
                cooled.add(new FishingSessionState.PackageCooldown(members, until, zoneId));
            }
        }
        return new FishingSessionState.OpportunityRevisit(cooled, lastMoveTargetTripWaypointIds(delivered));
    }

    public static List<UUID> lastMoveTargetTripWaypointIds(List<DeliveredDecision> delivered) {
        if (delivered == null || delivered.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (DeliveredDecision decision : delivered) {
            if (decision == null || decision.primaryAction() != GuidanceAction.MOVE) {
                continue;
            }
            if (decision.targetTripWaypointId() != null) {
                ids.add(decision.targetTripWaypointId());
            }
        }
        return ids;
    }

    public boolean meaningfulEffort(SessionWaypointProgress row) {
        if (row == null || row.getDepartedAt() == null) {
            return false;
        }
        WaypointProgressStatus status = row.getStatus();
        if (status == WaypointProgressStatus.UPCOMING || status == WaypointProgressStatus.NAVIGATING) {
            return false;
        }
        int dwell = row.getAccumulatedDwellSeconds();
        if (dwell <= 0 && row.getArrivedAt() != null) {
            dwell = (int) Math.max(0, Duration.between(row.getArrivedAt(), row.getDepartedAt()).getSeconds());
        }
        return dwell >= meaningfulDwellSeconds;
    }

    public static TargetKind visitKind(TripWaypoint waypoint) {
        if (waypoint == null) {
            return TargetKind.POINT;
        }
        if (waypoint.getVisitKind() != null) {
            return waypoint.getVisitKind();
        }
        String macro = TripWaypointPlanMetadata.macroVisitKind(waypoint.getMetadata());
        TargetKind parsed = parseKind(macro);
        if (parsed != null) {
            return parsed;
        }
        if (waypoint.getTargetKind() != null) {
            return waypoint.getTargetKind();
        }
        List<UUID> members = TripWaypointPlanMetadata.packageMemberIds(waypoint);
        if (members.size() > 1) {
            return TargetKind.ZONE;
        }
        return TargetKind.POINT;
    }

    public int cooldownMinutes(TargetKind kind) {
        if (kind == TargetKind.ZONE) {
            return cfg.getZonePackageCooldownMinutes();
        }
        if (kind != null && kind.isPathLike()) {
            return cfg.getPathCooldownMinutes();
        }
        return cfg.getPointCooldownMinutes();
    }

    public static List<UUID> packageMemberIds(TripWaypoint waypoint, UUID tripWaypointId) {
        List<UUID> members = TripWaypointPlanMetadata.packageMemberIds(waypoint);
        if (!members.isEmpty()) {
            return members;
        }
        if (waypoint != null && waypoint.getFishingTargetId() != null) {
            return List.of(waypoint.getFishingTargetId());
        }
        if (waypoint != null && waypoint.getLakeFeatureId() != null) {
            return List.of(waypoint.getLakeFeatureId());
        }
        return tripWaypointId == null ? List.of() : List.of(tripWaypointId);
    }

    public static List<UUID> packageMemberIds(OriginalPlanStep step, UUID fallbackTripWaypointId) {
        if (step != null && step.packageMemberIds() != null && !step.packageMemberIds().isEmpty()) {
            return step.packageMemberIds();
        }
        if (step != null && step.fishingTargetId() != null) {
            return List.of(step.fishingTargetId());
        }
        if (step != null && step.lakeFeatureId() != null) {
            return List.of(step.lakeFeatureId());
        }
        UUID id = step == null ? fallbackTripWaypointId : step.tripWaypointId();
        return id == null ? List.of() : List.of(id);
    }

    public static OriginalPlanStep originalStep(FishingSessionState state, UUID tripWaypointId) {
        if (state == null || state.plan() == null || tripWaypointId == null || state.plan().originalPlanSteps() == null) {
            return null;
        }
        for (OriginalPlanStep step : state.plan().originalPlanSteps()) {
            if (step != null && tripWaypointId.equals(step.tripWaypointId())) {
                return step;
            }
        }
        return null;
    }

    public boolean stillAtCurrent(FishingSessionState state) {
        if (state == null || state.fishing() == null) {
            return false;
        }
        if (state.fishing().activityState() == FishingActivityState.TRANSIT) {
            return false;
        }
        return state.fishing().activityState() == FishingActivityState.FISHING
                || (state.fishing().timeAtWaypointMinutes() != null && state.fishing().timeAtWaypointMinutes() > 0);
    }

    public boolean samePhysicalIdentity(UUID targetTripWaypointId, FishingSessionState state) {
        if (targetTripWaypointId == null || state == null || state.fishing() == null) {
            return false;
        }
        UUID current = state.fishing().currentTripWaypointId();
        if (targetTripWaypointId.equals(current)) {
            return true;
        }
        List<UUID> targetMembers = packageMemberIds(originalStep(state, targetTripWaypointId), targetTripWaypointId);
        List<UUID> currentMembers = packageMemberIds(originalStep(state, current), current);
        if (!targetMembers.isEmpty() && !currentMembers.isEmpty() && sameSet(targetMembers, currentMembers)) {
            return true;
        }
        OriginalPlanStep target = originalStep(state, targetTripWaypointId);
        OriginalPlanStep here = originalStep(state, current);
        if (target != null && here != null) {
            if (target.fishingTargetId() != null && target.fishingTargetId().equals(here.fishingTargetId())) {
                return true;
            }
            if (target.lakeFeatureId() != null && target.lakeFeatureId().equals(here.lakeFeatureId())) {
                return true;
            }
        }
        return false;
    }

    public boolean entirePackageCooling(List<UUID> packageMemberIds, FishingSessionState.OpportunityRevisit slice) {
        return entirePackageCooling(packageMemberIds, slice, now());
    }

    public boolean entirePackageCooling(
            List<UUID> packageMemberIds,
            FishingSessionState.OpportunityRevisit slice,
            Instant now
    ) {
        if (packageMemberIds == null || packageMemberIds.isEmpty()) {
            return false;
        }
        Set<UUID> cooling = activeCooledMemberIds(slice, now);
        for (UUID member : packageMemberIds) {
            if (member == null || !cooling.contains(member)) {
                return false;
            }
        }
        return true;
    }

    public Set<UUID> activeCooledMemberIds(FishingSessionState.OpportunityRevisit slice, Instant now) {
        Set<UUID> cooling = new HashSet<>();
        if (slice == null || slice.packageCooldowns() == null) {
            return cooling;
        }
        Instant at = now == null ? now() : now;
        for (FishingSessionState.PackageCooldown row : slice.packageCooldowns()) {
            if (row == null || row.cooldownUntil() == null || !row.cooldownUntil().isAfter(at)) {
                continue;
            }
            if (row.packageMemberIds() != null) {
                cooling.addAll(row.packageMemberIds());
            }
        }
        return cooling;
    }

    public String packageEligibility(List<UUID> packageMemberIds, FishingSessionState.OpportunityRevisit slice) {
        return entirePackageCooling(packageMemberIds, slice) ? COOLED : ELIGIBLE;
    }

    public boolean oscillatingMove(UUID targetTripWaypointId, FishingSessionState.OpportunityRevisit slice) {
        if (targetTripWaypointId == null) {
            return false;
        }
        List<UUID> delivered = slice == null || slice.lastMoveTargetTripWaypointIds() == null
                ? List.of()
                : slice.lastMoveTargetTripWaypointIds();
        int window = cfg.getOscillationWindowMoves();
        int priorNeeded = Math.max(1, window - 1);
        if (delivered.size() < priorNeeded) {
            return false;
        }
        List<UUID> recent = delivered.subList(delivered.size() - priorNeeded, delivered.size());
        UUID last = recent.getLast();
        if (targetTripWaypointId.equals(last)) {
            return false;
        }
        for (int i = 0; i < recent.size() - 1; i++) {
            if (targetTripWaypointId.equals(recent.get(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean materialEligibilityChange(CandidateDecision candidate, FishingSessionState state, UUID target) {
        GuidanceProperties.OpportunityRevisit.MaterialEligibility material = cfg.getMaterialEligibility();
        List<UUID> members = packageMemberIds(originalStep(state, target), target);
        if (material.isCooldownExpiry() && cooldownExpired(members, state == null ? null : state.opportunityRevisit())) {
            return true;
        }
        if (material.isTimeBucketChange() && timeBucketChanged(state)) {
            return true;
        }
        if (material.isWeatherChange() && matchesAny(candidate, state, "SIGNIFICANT_WEATHER", "WEATHER_UPDATED", "THUNDER")) {
            return true;
        }
        if (material.isLivePressureChange() && matchesAny(candidate, state, "LIVE_PRESSURE", "GET_LIVE_WAYPOINT", "ANGLERS")) {
            return true;
        }
        if (material.isNewToolEvidence() && matchesAny(
                candidate, state, "NEW_TOOL_EVIDENCE", "GET_NEARBY", "GET_WAYPOINT_STRUCTURE", "ELIGIBLE PACKAGE")) {
            return true;
        }
        if (material.isExplicitUserIntent() && explicitUserIntent(candidate, state)) {
            return true;
        }
        if (material.isBiteOrFishOnAtReturnTarget() && biteOrFishOnSignal(candidate, state)) {
            return true;
        }
        return false;
    }

    public boolean cooldownExpired(List<UUID> packageMemberIds, FishingSessionState.OpportunityRevisit slice) {
        if (packageMemberIds == null || packageMemberIds.isEmpty() || slice == null || slice.packageCooldowns() == null) {
            return false;
        }
        Instant at = now();
        boolean saw = false;
        boolean anyActive = false;
        for (FishingSessionState.PackageCooldown row : slice.packageCooldowns()) {
            if (row == null || row.packageMemberIds() == null || !coversAll(row.packageMemberIds(), packageMemberIds)) {
                continue;
            }
            saw = true;
            if (row.cooldownUntil() != null && row.cooldownUntil().isAfter(at)) {
                anyActive = true;
            }
        }
        return saw && !anyActive;
    }

    private boolean timeBucketChanged(FishingSessionState state) {
        if (state == null || state.fishing() == null) {
            return false;
        }
        Integer minutes = state.fishing().timeAtWaypointMinutes();
        return minutes != null && minutes >= cfg.getMaterialEligibility().getTimeChangeMinutes();
    }

    private boolean explicitUserIntent(CandidateDecision candidate, FishingSessionState state) {
        if (matchesAny(candidate, state, "USER_STARTED_AD_HOC", "FISH_HERE", "FISH HERE")) {
            return true;
        }
        if (state == null || state.fishing() == null) {
            return false;
        }
        if (state.fishing().activityStateSource() == ActivityStateSource.USER_AD_HOC) {
            return true;
        }
        return state.fishing().adHocFishingStopId() != null;
    }

    private static boolean biteOrFishOnSignal(CandidateDecision candidate, FishingSessionState state) {
        return matchesAny(candidate, state, "BITE", "FISH_ON", "FISH ON");
    }

    private static boolean matchesAny(CandidateDecision candidate, FishingSessionState state, String... tokens) {
        List<String> haystack = new ArrayList<>();
        if (candidate != null && candidate.reasonCodes() != null) {
            haystack.addAll(candidate.reasonCodes());
        }
        if (state != null && state.recent() != null && state.recent().summaries() != null) {
            haystack.addAll(state.recent().summaries());
        }
        for (String text : haystack) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String upper = text.toUpperCase(Locale.ROOT);
            for (String token : tokens) {
                if (upper.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean coversAll(List<UUID> coolingMembers, List<UUID> packageMemberIds) {
        Set<UUID> cooling = new HashSet<>(coolingMembers);
        for (UUID member : packageMemberIds) {
            if (!cooling.contains(member)) {
                return false;
            }
        }
        return !packageMemberIds.isEmpty();
    }

    private static boolean sameSet(List<UUID> left, List<UUID> right) {
        return new LinkedHashSet<>(left).equals(new LinkedHashSet<>(right));
    }

    private static TargetKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TargetKind.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
