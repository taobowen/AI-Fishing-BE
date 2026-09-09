package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "lake_access_points")
public class LakeAccessPoint extends CanonicalOntarioRecord {

    @Column(nullable = false)
    private String type;

    private String name;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "boat_launch")
    private Boolean boatLaunch;

    @Column(name = "shore_access")
    private Boolean shoreAccess;

    @Column(name = "road_access")
    private Boolean roadAccess;

    private Boolean parking;

    @Column(name = "ownership_type")
    private String ownershipType;

    @Column(name = "association_distance_meters")
    private Double associationDistanceMeters;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public Boolean getBoatLaunch() {
        return boatLaunch;
    }

    public void setBoatLaunch(Boolean boatLaunch) {
        this.boatLaunch = boatLaunch;
    }

    public Boolean getShoreAccess() {
        return shoreAccess;
    }

    public void setShoreAccess(Boolean shoreAccess) {
        this.shoreAccess = shoreAccess;
    }

    public Boolean getRoadAccess() {
        return roadAccess;
    }

    public void setRoadAccess(Boolean roadAccess) {
        this.roadAccess = roadAccess;
    }

    public Boolean getParking() {
        return parking;
    }

    public void setParking(Boolean parking) {
        this.parking = parking;
    }

    public String getOwnershipType() {
        return ownershipType;
    }

    public void setOwnershipType(String ownershipType) {
        this.ownershipType = ownershipType;
    }

    public Double getAssociationDistanceMeters() {
        return associationDistanceMeters;
    }

    public void setAssociationDistanceMeters(Double associationDistanceMeters) {
        this.associationDistanceMeters = associationDistanceMeters;
    }
}
