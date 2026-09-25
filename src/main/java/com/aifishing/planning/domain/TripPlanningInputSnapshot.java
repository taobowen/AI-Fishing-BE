package com.aifishing.planning.domain;

import com.aifishing.common.enums.PlanningMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_planning_input_snapshots")
public class TripPlanningInputSnapshot {

    @Id
    private UUID id;

    @Column(name = "planning_run_id", nullable = false, unique = true)
    private UUID planningRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanningMode mode;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_name", length = 128)
    private String templateName;

    @Column(name = "template_target_count", nullable = false)
    private int templateTargetCount;

    @Column(name = "required_point_count", nullable = false)
    private int requiredPointCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void ensureIdAndCreatedAt() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPlanningRunId() {
        return planningRunId;
    }

    public void setPlanningRunId(UUID planningRunId) {
        this.planningRunId = planningRunId;
    }

    public PlanningMode getMode() {
        return mode;
    }

    public void setMode(PlanningMode mode) {
        this.mode = mode;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public int getTemplateTargetCount() {
        return templateTargetCount;
    }

    public void setTemplateTargetCount(int templateTargetCount) {
        this.templateTargetCount = templateTargetCount;
    }

    public int getRequiredPointCount() {
        return requiredPointCount;
    }

    public void setRequiredPointCount(int requiredPointCount) {
        this.requiredPointCount = requiredPointCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
