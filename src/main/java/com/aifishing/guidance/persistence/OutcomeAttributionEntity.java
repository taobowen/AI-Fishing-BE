package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.AttributionWindowKind;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.RecommendationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outcome_attributions")
public class OutcomeAttributionEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "outcome_event_id", nullable = false)
    private UUID outcomeEventId;

    @Column(name = "fish_interaction_id")
    private UUID fishInteractionId;

    @Column(name = "delivered_decision_id")
    private UUID deliveredDecisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribution_dimension", nullable = false, length = 16)
    private AttributionDimension attributionDimension;

    @Column(name = "followed_recommendation", nullable = false)
    private boolean followedRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_role", nullable = false, length = 16)
    private RecommendationRole recommendationRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_kind", nullable = false, length = 16)
    private OutcomeKind outcomeKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_kind", length = 16)
    private AttributionWindowKind windowKind;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "attributed_at", nullable = false)
    private Instant attributedAt;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public UUID getOutcomeEventId() {
        return outcomeEventId;
    }

    public void setOutcomeEventId(UUID outcomeEventId) {
        this.outcomeEventId = outcomeEventId;
    }

    public UUID getFishInteractionId() {
        return fishInteractionId;
    }

    public void setFishInteractionId(UUID fishInteractionId) {
        this.fishInteractionId = fishInteractionId;
    }

    public UUID getDeliveredDecisionId() {
        return deliveredDecisionId;
    }

    public void setDeliveredDecisionId(UUID deliveredDecisionId) {
        this.deliveredDecisionId = deliveredDecisionId;
    }

    public AttributionDimension getAttributionDimension() {
        return attributionDimension;
    }

    public void setAttributionDimension(AttributionDimension attributionDimension) {
        this.attributionDimension = attributionDimension;
    }

    public boolean isFollowedRecommendation() {
        return followedRecommendation;
    }

    public void setFollowedRecommendation(boolean followedRecommendation) {
        this.followedRecommendation = followedRecommendation;
    }

    public RecommendationRole getRecommendationRole() {
        return recommendationRole;
    }

    public void setRecommendationRole(RecommendationRole recommendationRole) {
        this.recommendationRole = recommendationRole;
    }

    public OutcomeKind getOutcomeKind() {
        return outcomeKind;
    }

    public void setOutcomeKind(OutcomeKind outcomeKind) {
        this.outcomeKind = outcomeKind;
    }

    public AttributionWindowKind getWindowKind() {
        return windowKind;
    }

    public void setWindowKind(AttributionWindowKind windowKind) {
        this.windowKind = windowKind;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Instant getAttributedAt() {
        return attributedAt;
    }

    public void setAttributedAt(Instant attributedAt) {
        this.attributedAt = attributedAt;
    }
}
