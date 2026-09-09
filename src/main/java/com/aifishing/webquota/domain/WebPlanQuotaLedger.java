package com.aifishing.webquota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "web_plan_quota_ledger")
public class WebPlanQuotaLedger {

    @Id
    @Column(name = "planning_run_id")
    private UUID planningRunId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_plan_id", nullable = false)
    private UUID tripPlanId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getPlanningRunId() {
        return planningRunId;
    }

    public void setPlanningRunId(UUID planningRunId) {
        this.planningRunId = planningRunId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getTripPlanId() {
        return tripPlanId;
    }

    public void setTripPlanId(UUID tripPlanId) {
        this.tripPlanId = tripPlanId;
    }
}
