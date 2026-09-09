package com.aifishing.fishingsession.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "session_waypoint_progress")
public class SessionWaypointProgress {

    @Id
    private UUID id;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "trip_waypoint_id", nullable = false)
    private UUID tripWaypointId;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaypointProgressStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason")
    private WaypointSkipReason skipReason;

    @Column(name = "first_approached_at")
    private Instant firstApproachedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "accumulated_dwell_seconds", nullable = false)
    private int accumulatedDwellSeconds;

    @Column(name = "closest_distance_m")
    private BigDecimal closestDistanceM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
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

    public UUID getTripWaypointId() {
        return tripWaypointId;
    }

    public void setTripWaypointId(UUID tripWaypointId) {
        this.tripWaypointId = tripWaypointId;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public WaypointProgressStatus getStatus() {
        return status;
    }

    public void setStatus(WaypointProgressStatus status) {
        this.status = status;
    }

    public WaypointSkipReason getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(WaypointSkipReason skipReason) {
        this.skipReason = skipReason;
    }

    public Instant getFirstApproachedAt() {
        return firstApproachedAt;
    }

    public void setFirstApproachedAt(Instant firstApproachedAt) {
        this.firstApproachedAt = firstApproachedAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(Instant arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public Instant getDepartedAt() {
        return departedAt;
    }

    public void setDepartedAt(Instant departedAt) {
        this.departedAt = departedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getSkippedAt() {
        return skippedAt;
    }

    public void setSkippedAt(Instant skippedAt) {
        this.skippedAt = skippedAt;
    }

    public int getAccumulatedDwellSeconds() {
        return accumulatedDwellSeconds;
    }

    public void setAccumulatedDwellSeconds(int accumulatedDwellSeconds) {
        this.accumulatedDwellSeconds = accumulatedDwellSeconds;
    }

    public BigDecimal getClosestDistanceM() {
        return closestDistanceM;
    }

    public void setClosestDistanceM(BigDecimal closestDistanceM) {
        this.closestDistanceM = closestDistanceM;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
