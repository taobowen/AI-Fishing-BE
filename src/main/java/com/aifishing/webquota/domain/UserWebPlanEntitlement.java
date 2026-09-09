package com.aifishing.webquota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_web_plan_entitlements")
public class UserWebPlanEntitlement {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "successful_generations", nullable = false)
    private int successfulGenerations;

    @Column(name = "lifetime_limit", nullable = false)
    private int lifetimeLimit = 3;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public int getSuccessfulGenerations() {
        return successfulGenerations;
    }

    public void setSuccessfulGenerations(int successfulGenerations) {
        this.successfulGenerations = successfulGenerations;
    }

    public int getLifetimeLimit() {
        return lifetimeLimit;
    }

    public void setLifetimeLimit(int lifetimeLimit) {
        this.lifetimeLimit = lifetimeLimit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int remaining() {
        return Math.max(0, lifetimeLimit - successfulGenerations);
    }
}
