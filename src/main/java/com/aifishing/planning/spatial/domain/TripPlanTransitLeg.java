package com.aifishing.planning.spatial.domain;

import com.aifishing.planning.spatial.TransitEndpointKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip_plan_transit_legs")
public class TripPlanTransitLeg {

    @Id
    private UUID id;

    @Column(name = "trip_plan_id", nullable = false)
    private UUID tripPlanId;

    @Column(nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_kind", nullable = false)
    private TransitEndpointKind fromKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_kind", nullable = false)
    private TransitEndpointKind toKind;

    @Column(name = "from_visit_id")
    private UUID fromVisitId;

    @Column(name = "to_visit_id")
    private UUID toVisitId;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "transit_path", columnDefinition = "geometry(LineString,4326)")
    private LineString transitPath;

    @Column(name = "path_distance_meters")
    private BigDecimal pathDistanceMeters;

    @Column(name = "planned_travel_minutes")
    private BigDecimal plannedTravelMinutes;

    @Column(name = "source_water_path_id")
    private UUID sourceWaterPathId;

    @Column(name = "navigation_version")
    private String navigationVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void ensureId() {
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

    public UUID getTripPlanId() {
        return tripPlanId;
    }

    public void setTripPlanId(UUID tripPlanId) {
        this.tripPlanId = tripPlanId;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public TransitEndpointKind getFromKind() {
        return fromKind;
    }

    public void setFromKind(TransitEndpointKind fromKind) {
        this.fromKind = fromKind;
    }

    public TransitEndpointKind getToKind() {
        return toKind;
    }

    public void setToKind(TransitEndpointKind toKind) {
        this.toKind = toKind;
    }

    public UUID getFromVisitId() {
        return fromVisitId;
    }

    public void setFromVisitId(UUID fromVisitId) {
        this.fromVisitId = fromVisitId;
    }

    public UUID getToVisitId() {
        return toVisitId;
    }

    public void setToVisitId(UUID toVisitId) {
        this.toVisitId = toVisitId;
    }

    public LineString getTransitPath() {
        return transitPath;
    }

    public void setTransitPath(LineString transitPath) {
        this.transitPath = transitPath;
    }

    public BigDecimal getPathDistanceMeters() {
        return pathDistanceMeters;
    }

    public void setPathDistanceMeters(BigDecimal pathDistanceMeters) {
        this.pathDistanceMeters = pathDistanceMeters;
    }

    public BigDecimal getPlannedTravelMinutes() {
        return plannedTravelMinutes;
    }

    public void setPlannedTravelMinutes(BigDecimal plannedTravelMinutes) {
        this.plannedTravelMinutes = plannedTravelMinutes;
    }

    public UUID getSourceWaterPathId() {
        return sourceWaterPathId;
    }

    public void setSourceWaterPathId(UUID sourceWaterPathId) {
        this.sourceWaterPathId = sourceWaterPathId;
    }

    public String getNavigationVersion() {
        return navigationVersion;
    }

    public void setNavigationVersion(String navigationVersion) {
        this.navigationVersion = navigationVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
