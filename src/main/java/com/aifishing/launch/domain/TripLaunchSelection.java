package com.aifishing.launch.domain;

import com.aifishing.common.domain.AuditedEntity;
import com.aifishing.common.enums.CustomAccessType;
import com.aifishing.common.enums.LaunchSelectionMode;
import com.aifishing.common.enums.ShorelineKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trip_launch_selections")
public class TripLaunchSelection extends AuditedEntity {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LaunchSelectionMode mode;

    @Column(name = "official_access_point_id")
    private UUID officialAccessPointId;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "requested_point", columnDefinition = "geometry(Point,4326)")
    private Point requestedPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "shore_access_point", columnDefinition = "geometry(Point,4326)")
    private Point shoreAccessPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "route_start_point", columnDefinition = "geometry(Point,4326)")
    private Point routeStartPoint;

    @Column(name = "snap_distance_m")
    private BigDecimal snapDistanceM;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_access_type")
    private CustomAccessType customAccessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "shoreline_kind")
    private ShorelineKind shorelineKind;

    @Column(name = "resolution_version")
    private String resolutionVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> warnings = new ArrayList<>();

    @Override
    public UUID id() {
        return tripId;
    }

    @Override
    protected void assignId(UUID id) {
        this.tripId = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public LaunchSelectionMode getMode() {
        return mode;
    }

    public void setMode(LaunchSelectionMode mode) {
        this.mode = mode;
    }

    public UUID getOfficialAccessPointId() {
        return officialAccessPointId;
    }

    public void setOfficialAccessPointId(UUID officialAccessPointId) {
        this.officialAccessPointId = officialAccessPointId;
    }

    public Point getRequestedPoint() {
        return requestedPoint;
    }

    public void setRequestedPoint(Point requestedPoint) {
        this.requestedPoint = requestedPoint;
    }

    public Point getShoreAccessPoint() {
        return shoreAccessPoint;
    }

    public void setShoreAccessPoint(Point shoreAccessPoint) {
        this.shoreAccessPoint = shoreAccessPoint;
    }

    public Point getRouteStartPoint() {
        return routeStartPoint;
    }

    public void setRouteStartPoint(Point routeStartPoint) {
        this.routeStartPoint = routeStartPoint;
    }

    public BigDecimal getSnapDistanceM() {
        return snapDistanceM;
    }

    public void setSnapDistanceM(BigDecimal snapDistanceM) {
        this.snapDistanceM = snapDistanceM;
    }

    public CustomAccessType getCustomAccessType() {
        return customAccessType;
    }

    public void setCustomAccessType(CustomAccessType customAccessType) {
        this.customAccessType = customAccessType;
    }

    public ShorelineKind getShorelineKind() {
        return shorelineKind;
    }

    public void setShorelineKind(ShorelineKind shorelineKind) {
        this.shorelineKind = shorelineKind;
    }

    public String getResolutionVersion() {
        return resolutionVersion;
    }

    public void setResolutionVersion(String resolutionVersion) {
        this.resolutionVersion = resolutionVersion;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }
}
