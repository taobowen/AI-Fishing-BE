package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.GuidanceTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "guidance_trigger_outbox")
public class GuidanceTriggerOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_trigger", nullable = false, length = 64)
    private GuidanceTrigger primaryTrigger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_triggers", nullable = false, columnDefinition = "jsonb")
    private List<GuidanceTrigger> relatedTriggers = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reason_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> reasonCodes = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GuidanceTriggerOutboxSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GuidanceTriggerOutboxStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_token")
    private UUID claimToken;

    @PrePersist
    void ensureIdAndCreatedAt() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (relatedTriggers == null) {
            relatedTriggers = List.of();
        }
        if (reasonCodes == null) {
            reasonCodes = List.of();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public GuidanceTrigger getPrimaryTrigger() {
        return primaryTrigger;
    }

    public void setPrimaryTrigger(GuidanceTrigger primaryTrigger) {
        this.primaryTrigger = primaryTrigger;
    }

    public List<GuidanceTrigger> getRelatedTriggers() {
        return relatedTriggers;
    }

    public void setRelatedTriggers(List<GuidanceTrigger> relatedTriggers) {
        this.relatedTriggers = relatedTriggers == null ? List.of() : relatedTriggers;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(List<String> reasonCodes) {
        this.reasonCodes = reasonCodes == null ? List.of() : reasonCodes;
    }

    public GuidanceTriggerOutboxSource getSource() {
        return source;
    }

    public void setSource(GuidanceTriggerOutboxSource source) {
        this.source = source;
    }

    public GuidanceTriggerOutboxStatus getStatus() {
        return status;
    }

    public void setStatus(GuidanceTriggerOutboxStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public UUID getClaimToken() {
        return claimToken;
    }

    public void setClaimToken(UUID claimToken) {
        this.claimToken = claimToken;
    }
}
