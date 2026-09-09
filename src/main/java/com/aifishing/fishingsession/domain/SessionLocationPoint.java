package com.aifishing.fishingsession.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "session_location_points")
public class SessionLocationPoint {

    @Id
    private UUID id;

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "accuracy_m")
    private BigDecimal accuracyM;

    @Column(name = "altitude_m")
    private BigDecimal altitudeM;

    @Column(name = "speed_mps")
    private BigDecimal speedMps;

    @Column(name = "heading_degrees")
    private BigDecimal headingDegrees;

    @Column(name = "client_point_id", nullable = false)
    private String clientPointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationQuality quality;

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

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
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

    public BigDecimal getAccuracyM() {
        return accuracyM;
    }

    public void setAccuracyM(BigDecimal accuracyM) {
        this.accuracyM = accuracyM;
    }

    public BigDecimal getAltitudeM() {
        return altitudeM;
    }

    public void setAltitudeM(BigDecimal altitudeM) {
        this.altitudeM = altitudeM;
    }

    public BigDecimal getSpeedMps() {
        return speedMps;
    }

    public void setSpeedMps(BigDecimal speedMps) {
        this.speedMps = speedMps;
    }

    public BigDecimal getHeadingDegrees() {
        return headingDegrees;
    }

    public void setHeadingDegrees(BigDecimal headingDegrees) {
        this.headingDegrees = headingDegrees;
    }

    public String getClientPointId() {
        return clientPointId;
    }

    public void setClientPointId(String clientPointId) {
        this.clientPointId = clientPointId;
    }

    public LocationQuality getQuality() {
        return quality;
    }

    public void setQuality(LocationQuality quality) {
        this.quality = quality;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
