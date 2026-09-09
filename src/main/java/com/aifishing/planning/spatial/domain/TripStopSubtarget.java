package com.aifishing.planning.spatial.domain;

import com.aifishing.planning.spatial.TargetKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trip_stop_subtargets")
public class TripStopSubtarget {

    @Id
    private UUID id;

    @Column(name = "trip_waypoint_id", nullable = false)
    private UUID tripWaypointId;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false)
    private TargetKind targetKind;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "entry_point", columnDefinition = "geometry(Point,4326)")
    private Point entryPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "exit_point", columnDefinition = "geometry(Point,4326)")
    private Point exitPoint;

    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;

    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;

    @Column(name = "planned_fishing_minutes")
    private Integer plannedFishingMinutes;

    @Column(name = "planned_internal_transit_minutes")
    private Integer plannedInternalTransitMinutes;

    private BigDecimal score;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> tactical;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public TargetKind getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Point getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(Point entryPoint) {
        this.entryPoint = entryPoint;
    }

    public Point getExitPoint() {
        return exitPoint;
    }

    public void setExitPoint(Point exitPoint) {
        this.exitPoint = exitPoint;
    }

    public Instant getPlannedArrivalAt() {
        return plannedArrivalAt;
    }

    public void setPlannedArrivalAt(Instant plannedArrivalAt) {
        this.plannedArrivalAt = plannedArrivalAt;
    }

    public Instant getPlannedDepartureAt() {
        return plannedDepartureAt;
    }

    public void setPlannedDepartureAt(Instant plannedDepartureAt) {
        this.plannedDepartureAt = plannedDepartureAt;
    }

    public Integer getPlannedFishingMinutes() {
        return plannedFishingMinutes;
    }

    public void setPlannedFishingMinutes(Integer plannedFishingMinutes) {
        this.plannedFishingMinutes = plannedFishingMinutes;
    }

    public Integer getPlannedInternalTransitMinutes() {
        return plannedInternalTransitMinutes;
    }

    public void setPlannedInternalTransitMinutes(Integer plannedInternalTransitMinutes) {
        this.plannedInternalTransitMinutes = plannedInternalTransitMinutes;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Map<String, Object> getTactical() {
        return tactical;
    }

    public void setTactical(Map<String, Object> tactical) {
        this.tactical = tactical;
    }
}
