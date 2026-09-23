package com.aifishing.fishingsession.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_ad_hoc_fishing_stops")
public class SessionAdHocFishingStop {

    @Id
    private UUID id;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "client_event_id", nullable = false, length = 128)
    private String clientEventId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "gps_accuracy_m")
    private BigDecimal gpsAccuracyM;

    @Column(name = "lake_feature_id")
    private UUID lakeFeatureId;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "subtarget_id")
    private UUID subtargetId;

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

    public String getClientEventId() {
        return clientEventId;
    }

    public void setClientEventId(String clientEventId) {
        this.clientEventId = clientEventId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public boolean covers(Instant at) {
        if (at == null || startedAt == null || at.isBefore(startedAt)) {
            return false;
        }
        return endedAt == null || at.isBefore(endedAt);
    }
}
