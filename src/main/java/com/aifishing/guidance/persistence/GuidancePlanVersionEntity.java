package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.PlanCreatedBy;
import com.aifishing.guidance.contracts.ReplanScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "guidance_plan_versions")
public class GuidancePlanVersionEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(nullable = false)
    private int version;

    @Column(name = "parent_version")
    private Integer parentVersion;

    @Column(name = "replan_reason", length = 128)
    private String replanReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "replan_scope", length = 32)
    private ReplanScope replanScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 16)
    private PlanCreatedBy createdBy;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Integer getParentVersion() {
        return parentVersion;
    }

    public void setParentVersion(Integer parentVersion) {
        this.parentVersion = parentVersion;
    }

    public String getReplanReason() {
        return replanReason;
    }

    public void setReplanReason(String replanReason) {
        this.replanReason = replanReason;
    }

    public ReplanScope getReplanScope() {
        return replanScope;
    }

    public void setReplanScope(ReplanScope replanScope) {
        this.replanScope = replanScope;
    }

    public PlanCreatedBy getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(PlanCreatedBy createdBy) {
        this.createdBy = createdBy;
    }
}
