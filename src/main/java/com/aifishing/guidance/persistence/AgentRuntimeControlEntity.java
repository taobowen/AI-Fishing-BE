package com.aifishing.guidance.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agent_runtime_control")
public class AgentRuntimeControlEntity {

    @Id
    @Column(nullable = false)
    private Short id = 1;

    @Column(name = "agent_enabled", nullable = false)
    private boolean agentEnabled = true;

    @Column(name = "production_version", nullable = false, length = 64)
    private String productionVersion;

    @Column(name = "candidate_version", length = 64)
    private String candidateVersion;

    @Column(name = "shadow_enabled", nullable = false)
    private boolean shadowEnabled;

    @Column(name = "learning_enabled", nullable = false)
    private boolean learningEnabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public boolean isAgentEnabled() {
        return agentEnabled;
    }

    public void setAgentEnabled(boolean agentEnabled) {
        this.agentEnabled = agentEnabled;
    }

    public String getProductionVersion() {
        return productionVersion;
    }

    public void setProductionVersion(String productionVersion) {
        this.productionVersion = productionVersion;
    }

    public String getCandidateVersion() {
        return candidateVersion;
    }

    public void setCandidateVersion(String candidateVersion) {
        this.candidateVersion = candidateVersion;
    }

    public boolean isShadowEnabled() {
        return shadowEnabled;
    }

    public void setShadowEnabled(boolean shadowEnabled) {
        this.shadowEnabled = shadowEnabled;
    }

    public boolean isLearningEnabled() {
        return learningEnabled;
    }

    public void setLearningEnabled(boolean learningEnabled) {
        this.learningEnabled = learningEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
