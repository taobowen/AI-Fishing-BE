package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.GuidanceAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "guidance_plan_steps")
public class GuidancePlanStepEntity extends GuidanceCreatedEntity {

    @Column(name = "guidance_plan_version_id", nullable = false)
    private UUID guidancePlanVersionId;

    @Column(nullable = false)
    private int step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GuidanceAction type;

    @Column(nullable = false)
    private boolean committed;

    @Column(name = "trip_waypoint_id")
    private UUID tripWaypointId;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    public UUID getGuidancePlanVersionId() {
        return guidancePlanVersionId;
    }

    public void setGuidancePlanVersionId(UUID guidancePlanVersionId) {
        this.guidancePlanVersionId = guidancePlanVersionId;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public GuidanceAction getType() {
        return type;
    }

    public void setType(GuidanceAction type) {
        this.type = type;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public UUID getTripWaypointId() {
        return tripWaypointId;
    }

    public void setTripWaypointId(UUID tripWaypointId) {
        this.tripWaypointId = tripWaypointId;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
