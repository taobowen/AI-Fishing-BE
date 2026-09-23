package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.ReflectionCauseKind;
import com.aifishing.guidance.contracts.ReflectionClaimKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "agent_reflections")
public class AgentReflectionEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "run_id")
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_kind", nullable = false, length = 32)
    private ReflectionClaimKind claimKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause_kind", length = 32)
    private ReflectionCauseKind causeKind;

    @Column(nullable = false)
    private String text;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public ReflectionClaimKind getClaimKind() {
        return claimKind;
    }

    public void setClaimKind(ReflectionClaimKind claimKind) {
        this.claimKind = claimKind;
    }

    public ReflectionCauseKind getCauseKind() {
        return causeKind;
    }

    public void setCauseKind(ReflectionCauseKind causeKind) {
        this.causeKind = causeKind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
