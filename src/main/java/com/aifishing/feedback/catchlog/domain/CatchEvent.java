package com.aifishing.feedback.catchlog.domain;

import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
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
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "catch_events")
public class CatchEvent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "trip_plan_id")
    private UUID tripPlanId;

    @Column(name = "trip_waypoint_id")
    private UUID tripWaypointId;

    @Column(name = "lake_feature_id")
    private UUID lakeFeatureId;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "subtarget_id")
    private UUID subtargetId;

    @Column(name = "along_track_fraction")
    private BigDecimal alongTrackFraction;

    @Column(name = "client_catch_id", nullable = false)
    private String clientCatchId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "gps_accuracy_m")
    private BigDecimal gpsAccuracyM;

    @Enumerated(EnumType.STRING)
    @Column(name = "association_method", nullable = false)
    private CatchAssociationMethod associationMethod;

    @Column(name = "distance_to_waypoint_m")
    private BigDecimal distanceToWaypointM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CatchOutcome outcome;

    @Enumerated(EnumType.STRING)
    private FishSpecies species;

    @Column(name = "length_cm")
    private BigDecimal lengthCm;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "technique_type")
    private TechniqueType techniqueType;

    @Column(name = "lure_name", length = 128)
    private String lureName;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_feature_type")
    private FeatureType plannedFeatureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_target_species", nullable = false)
    private FishSpecies primaryTargetSpecies;

    @Column(name = "strategy_run_id")
    private UUID strategyRunId;

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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public UUID getTripPlanId() {
        return tripPlanId;
    }

    public void setTripPlanId(UUID tripPlanId) {
        this.tripPlanId = tripPlanId;
    }

    public UUID getTripWaypointId() {
        return tripWaypointId;
    }

    public void setTripWaypointId(UUID tripWaypointId) {
        this.tripWaypointId = tripWaypointId;
    }

    public UUID getLakeFeatureId() {
        return lakeFeatureId;
    }

    public void setLakeFeatureId(UUID lakeFeatureId) {
        this.lakeFeatureId = lakeFeatureId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public UUID getSubtargetId() {
        return subtargetId;
    }

    public void setSubtargetId(UUID subtargetId) {
        this.subtargetId = subtargetId;
    }

    public BigDecimal getAlongTrackFraction() {
        return alongTrackFraction;
    }

    public void setAlongTrackFraction(BigDecimal alongTrackFraction) {
        this.alongTrackFraction = alongTrackFraction;
    }

    public String getClientCatchId() {
        return clientCatchId;
    }

    public void setClientCatchId(String clientCatchId) {
        this.clientCatchId = clientCatchId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public BigDecimal getGpsAccuracyM() {
        return gpsAccuracyM;
    }

    public void setGpsAccuracyM(BigDecimal gpsAccuracyM) {
        this.gpsAccuracyM = gpsAccuracyM;
    }

    public CatchAssociationMethod getAssociationMethod() {
        return associationMethod;
    }

    public void setAssociationMethod(CatchAssociationMethod associationMethod) {
        this.associationMethod = associationMethod;
    }

    public BigDecimal getDistanceToWaypointM() {
        return distanceToWaypointM;
    }

    public void setDistanceToWaypointM(BigDecimal distanceToWaypointM) {
        this.distanceToWaypointM = distanceToWaypointM;
    }

    public CatchStatus getStatus() {
        return status;
    }

    public void setStatus(CatchStatus status) {
        this.status = status;
    }

    public CatchOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(CatchOutcome outcome) {
        this.outcome = outcome;
    }

    public FishSpecies getSpecies() {
        return species;
    }

    public void setSpecies(FishSpecies species) {
        this.species = species;
    }

    public BigDecimal getLengthCm() {
        return lengthCm;
    }

    public void setLengthCm(BigDecimal lengthCm) {
        this.lengthCm = lengthCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public TechniqueType getTechniqueType() {
        return techniqueType;
    }

    public void setTechniqueType(TechniqueType techniqueType) {
        this.techniqueType = techniqueType;
    }

    public String getLureName() {
        return lureName;
    }

    public void setLureName(String lureName) {
        this.lureName = lureName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public FeatureType getPlannedFeatureType() {
        return plannedFeatureType;
    }

    public void setPlannedFeatureType(FeatureType plannedFeatureType) {
        this.plannedFeatureType = plannedFeatureType;
    }

    public FishSpecies getPrimaryTargetSpecies() {
        return primaryTargetSpecies;
    }

    public void setPrimaryTargetSpecies(FishSpecies primaryTargetSpecies) {
        this.primaryTargetSpecies = primaryTargetSpecies;
    }

    public UUID getStrategyRunId() {
        return strategyRunId;
    }

    public void setStrategyRunId(UUID strategyRunId) {
        this.strategyRunId = strategyRunId;
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
