package com.aifishing.fishingsession.domain;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fishing_sessions")
public class FishingSession {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trip_plan_id")
    private UUID tripPlanId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FishingSessionStatus status;

    @Column(name = "plan_version")
    private Integer planVersion;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "total_paused_seconds", nullable = false)
    private int totalPausedSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_state", nullable = false, length = 16)
    private FishingActivityState activityState = FishingActivityState.UNKNOWN;

    @Column(name = "activity_state_since")
    private Instant activityStateSince;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_state_source", nullable = false, length = 32)
    private ActivityStateSource activityStateSource = ActivityStateSource.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_state_override", length = 16)
    private FishingActivityState activityStateOverride;

    @Column(name = "activity_state_override_since")
    private Instant activityStateOverrideSince;

    @Enumerated(EnumType.STRING)
    @Column(name = "guidance_mode", nullable = false, length = 32)
    private SessionGuidanceMode guidanceMode = SessionGuidanceMode.AGENT_GUIDED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureIdAndCreatedAt() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (activityState == null) {
            activityState = FishingActivityState.UNKNOWN;
        }
        if (activityStateSource == null) {
            activityStateSource = ActivityStateSource.UNKNOWN;
        }
        if (guidanceMode == null) {
            guidanceMode = SessionGuidanceMode.AGENT_GUIDED;
        }
        if (activityStateSince == null) {
            activityStateSince = startedAt != null ? startedAt : createdAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public FishingSessionStatus getStatus() {
        return status;
    }

    public void setStatus(FishingSessionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getPlanVersion() {
        return planVersion;
    }

    public void setPlanVersion(Integer planVersion) {
        this.planVersion = planVersion;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public void setPausedAt(Instant pausedAt) {
        this.pausedAt = pausedAt;
    }

    public int getTotalPausedSeconds() {
        return totalPausedSeconds;
    }

    public void setTotalPausedSeconds(int totalPausedSeconds) {
        this.totalPausedSeconds = totalPausedSeconds;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public FishingActivityState getActivityState() {
        return activityState;
    }

    public void setActivityState(FishingActivityState activityState) {
        this.activityState = activityState;
    }

    public Instant getActivityStateSince() {
        return activityStateSince;
    }

    public void setActivityStateSince(Instant activityStateSince) {
        this.activityStateSince = activityStateSince;
    }

    public ActivityStateSource getActivityStateSource() {
        return activityStateSource;
    }

    public void setActivityStateSource(ActivityStateSource activityStateSource) {
        this.activityStateSource = activityStateSource;
    }

    public FishingActivityState getActivityStateOverride() {
        return activityStateOverride;
    }

    public void setActivityStateOverride(FishingActivityState activityStateOverride) {
        this.activityStateOverride = activityStateOverride;
    }

    public Instant getActivityStateOverrideSince() {
        return activityStateOverrideSince;
    }

    public void setActivityStateOverrideSince(Instant activityStateOverrideSince) {
        this.activityStateOverrideSince = activityStateOverrideSince;
    }

    public SessionGuidanceMode getGuidanceMode() {
        return SessionGuidanceMode.orDefault(guidanceMode);
    }

    public void setGuidanceMode(SessionGuidanceMode guidanceMode) {
        this.guidanceMode = SessionGuidanceMode.orDefault(guidanceMode);
    }
}
