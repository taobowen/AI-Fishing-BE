package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.RecommendationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_action_events")
public class UserActionEventEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "delivered_decision_id")
    private UUID deliveredDecisionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "followed_primary", nullable = false)
    private boolean followedPrimary;

    @Column(name = "followed_recommendation")
    private Boolean followedRecommendation;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_role", length = 16)
    private RecommendationRole recommendationRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_action", length = 32)
    private GuidanceAction actualAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public UUID getDeliveredDecisionId() {
        return deliveredDecisionId;
    }

    public void setDeliveredDecisionId(UUID deliveredDecisionId) {
        this.deliveredDecisionId = deliveredDecisionId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public boolean isFollowedPrimary() {
        return followedPrimary;
    }

    public void setFollowedPrimary(boolean followedPrimary) {
        this.followedPrimary = followedPrimary;
    }

    public Boolean getFollowedRecommendation() {
        return followedRecommendation;
    }

    public void setFollowedRecommendation(Boolean followedRecommendation) {
        this.followedRecommendation = followedRecommendation;
    }

    public RecommendationRole getRecommendationRole() {
        return recommendationRole;
    }

    public void setRecommendationRole(RecommendationRole recommendationRole) {
        this.recommendationRole = recommendationRole;
    }

    public GuidanceAction getActualAction() {
        return actualAction;
    }

    public void setActualAction(GuidanceAction actualAction) {
        this.actualAction = actualAction;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
