package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.FeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_feedback")
public class AgentFeedbackEntity extends GuidanceCreatedEntity {

    @Column(name = "delivered_decision_id", nullable = false)
    private UUID deliveredDecisionId;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FeedbackStatus status;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(length = 2000)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public UUID getDeliveredDecisionId() {
        return deliveredDecisionId;
    }

    public void setDeliveredDecisionId(UUID deliveredDecisionId) {
        this.deliveredDecisionId = deliveredDecisionId;
    }

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public void setStatus(FeedbackStatus status) {
        this.status = status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
