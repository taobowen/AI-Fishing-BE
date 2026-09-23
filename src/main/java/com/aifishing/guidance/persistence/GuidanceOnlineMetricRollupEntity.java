package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "guidance_online_metric_rollups")
public class GuidanceOnlineMetricRollupEntity extends GuidanceCreatedEntity {

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OnlineMetricGrain grain;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_dimension", length = 16)
    private AttributionDimension attributionDimension;

    @Column(name = "fish_on_success_count", nullable = false)
    private long fishOnSuccessCount;

    @Column(name = "bite_signal_only_count", nullable = false)
    private long biteSignalOnlyCount;

    @Column(name = "no_fish_signal_count", nullable = false)
    private long noFishSignalCount;

    @Column(name = "not_followed_count", nullable = false)
    private long notFollowedCount;

    @Column(name = "unattributed_count", nullable = false)
    private long unattributedCount;

    @Column(name = "followed_recommendation_count", nullable = false)
    private long followedRecommendationCount;

    @Column(name = "explicit_accepted_count", nullable = false)
    private long explicitAcceptedCount;

    @Column(name = "explicit_partial_count", nullable = false)
    private long explicitPartialCount;

    @Column(name = "reject_count", nullable = false)
    private long rejectCount;

    @Column(name = "override_count", nullable = false)
    private long overrideCount;

    @Column(name = "follow_through_eligible_count", nullable = false)
    private long followThroughEligibleCount;

    @Column(name = "override_eligible_count", nullable = false)
    private long overrideEligibleCount;

    @Column(name = "feedback_count", nullable = false)
    private long feedbackCount;

    @Column(name = "candidate_count", nullable = false)
    private long candidateCount;

    @Column(name = "delivered_count", nullable = false)
    private long deliveredCount;

    @Column(name = "candidate_unsafe_count", nullable = false)
    private long candidateUnsafeCount;

    @Column(name = "candidate_invalid_waypoint_count", nullable = false)
    private long candidateInvalidWaypointCount;

    @Column(name = "validator_interception_count", nullable = false)
    private long validatorInterceptionCount;

    @Column(name = "unsafe_delivered_count", nullable = false)
    private long unsafeDeliveredCount;

    @Column(name = "invalid_delivered_waypoint_count", nullable = false)
    private long invalidDeliveredWaypointCount;

    @Column(name = "effective_fishing_effort_seconds", nullable = false)
    private long effectiveFishingEffortSeconds;

    @Column(name = "pricing_version", length = 64)
    private String pricingVersion;

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public OnlineMetricGrain getGrain() {
        return grain;
    }

    public void setGrain(OnlineMetricGrain grain) {
        this.grain = grain;
    }

    public AttributionDimension getAttributionDimension() {
        return attributionDimension;
    }

    public void setAttributionDimension(AttributionDimension attributionDimension) {
        this.attributionDimension = attributionDimension;
    }

    public long getFishOnSuccessCount() {
        return fishOnSuccessCount;
    }

    public void setFishOnSuccessCount(long fishOnSuccessCount) {
        this.fishOnSuccessCount = fishOnSuccessCount;
    }

    public long getBiteSignalOnlyCount() {
        return biteSignalOnlyCount;
    }

    public void setBiteSignalOnlyCount(long biteSignalOnlyCount) {
        this.biteSignalOnlyCount = biteSignalOnlyCount;
    }

    public long getNoFishSignalCount() {
        return noFishSignalCount;
    }

    public void setNoFishSignalCount(long noFishSignalCount) {
        this.noFishSignalCount = noFishSignalCount;
    }

    public long getNotFollowedCount() {
        return notFollowedCount;
    }

    public void setNotFollowedCount(long notFollowedCount) {
        this.notFollowedCount = notFollowedCount;
    }

    public long getUnattributedCount() {
        return unattributedCount;
    }

    public void setUnattributedCount(long unattributedCount) {
        this.unattributedCount = unattributedCount;
    }

    public long getFollowedRecommendationCount() {
        return followedRecommendationCount;
    }

    public void setFollowedRecommendationCount(long followedRecommendationCount) {
        this.followedRecommendationCount = followedRecommendationCount;
    }

    public long getExplicitAcceptedCount() {
        return explicitAcceptedCount;
    }

    public void setExplicitAcceptedCount(long explicitAcceptedCount) {
        this.explicitAcceptedCount = explicitAcceptedCount;
    }

    public long getExplicitPartialCount() {
        return explicitPartialCount;
    }

    public void setExplicitPartialCount(long explicitPartialCount) {
        this.explicitPartialCount = explicitPartialCount;
    }

    public long getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(long rejectCount) {
        this.rejectCount = rejectCount;
    }

    public long getOverrideCount() {
        return overrideCount;
    }

    public void setOverrideCount(long overrideCount) {
        this.overrideCount = overrideCount;
    }

    public long getFollowThroughEligibleCount() {
        return followThroughEligibleCount;
    }

    public void setFollowThroughEligibleCount(long followThroughEligibleCount) {
        this.followThroughEligibleCount = followThroughEligibleCount;
    }

    public long getOverrideEligibleCount() {
        return overrideEligibleCount;
    }

    public void setOverrideEligibleCount(long overrideEligibleCount) {
        this.overrideEligibleCount = overrideEligibleCount;
    }

    public long getFeedbackCount() {
        return feedbackCount;
    }

    public void setFeedbackCount(long feedbackCount) {
        this.feedbackCount = feedbackCount;
    }

    public long getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(long candidateCount) {
        this.candidateCount = candidateCount;
    }

    public long getDeliveredCount() {
        return deliveredCount;
    }

    public void setDeliveredCount(long deliveredCount) {
        this.deliveredCount = deliveredCount;
    }

    public long getCandidateUnsafeCount() {
        return candidateUnsafeCount;
    }

    public void setCandidateUnsafeCount(long candidateUnsafeCount) {
        this.candidateUnsafeCount = candidateUnsafeCount;
    }

    public long getCandidateInvalidWaypointCount() {
        return candidateInvalidWaypointCount;
    }

    public void setCandidateInvalidWaypointCount(long candidateInvalidWaypointCount) {
        this.candidateInvalidWaypointCount = candidateInvalidWaypointCount;
    }

    public long getValidatorInterceptionCount() {
        return validatorInterceptionCount;
    }

    public void setValidatorInterceptionCount(long validatorInterceptionCount) {
        this.validatorInterceptionCount = validatorInterceptionCount;
    }

    public long getUnsafeDeliveredCount() {
        return unsafeDeliveredCount;
    }

    public void setUnsafeDeliveredCount(long unsafeDeliveredCount) {
        this.unsafeDeliveredCount = unsafeDeliveredCount;
    }

    public long getInvalidDeliveredWaypointCount() {
        return invalidDeliveredWaypointCount;
    }

    public void setInvalidDeliveredWaypointCount(long invalidDeliveredWaypointCount) {
        this.invalidDeliveredWaypointCount = invalidDeliveredWaypointCount;
    }

    public long getEffectiveFishingEffortSeconds() {
        return effectiveFishingEffortSeconds;
    }

    public void setEffectiveFishingEffortSeconds(long effectiveFishingEffortSeconds) {
        this.effectiveFishingEffortSeconds = effectiveFishingEffortSeconds;
    }

    public String getPricingVersion() {
        return pricingVersion;
    }

    public void setPricingVersion(String pricingVersion) {
        this.pricingVersion = pricingVersion;
    }
}
