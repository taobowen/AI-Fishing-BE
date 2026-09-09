package com.aifishing.boat.domain;

import com.aifishing.common.domain.AuditedEntity;
import com.aifishing.common.enums.BoatProvenance;
import com.aifishing.common.enums.BoatType;
import com.aifishing.common.enums.PropulsionType;
import com.aifishing.common.enums.WindWaveCapability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "boats")
public class Boat extends AuditedEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoatType type;

    @Column
    private String manufacturer;

    private String model;

    private Integer year;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "propulsion_types", nullable = false, columnDefinition = "jsonb")
    private List<PropulsionType> propulsionTypes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_transit_propulsion_type", nullable = false)
    private PropulsionType primaryTransitPropulsionType = PropulsionType.NONE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<BoatMotor> motors = new ArrayList<>();

    @Column(name = "configuration_description")
    private String configurationDescription;

    @Column(name = "max_speed_kmh")
    private BigDecimal maxSpeedKmh;

    @Column(name = "measured_cruise_speed_kmh")
    private BigDecimal measuredCruiseSpeedKmh;

    @Column(name = "comfortable_round_trip_range_km")
    private BigDecimal comfortableRoundTripRangeKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_wave_override")
    private WindWaveCapability windWaveOverride;

    @Column(name = "free_text_hash")
    private String freeTextHash;

    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "system_generated", nullable = false)
    private boolean systemGenerated = false;

    @Enumerated(EnumType.STRING)
    @Column
    private BoatProvenance provenance;

    @Override
    public UUID id() {
        return id;
    }

    @Override
    protected void assignId(UUID id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BoatType getType() {
        return type;
    }

    public void setType(BoatType type) {
        this.type = type;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public List<PropulsionType> getPropulsionTypes() {
        return propulsionTypes;
    }

    public void setPropulsionTypes(List<PropulsionType> propulsionTypes) {
        this.propulsionTypes = propulsionTypes == null ? new ArrayList<>() : new ArrayList<>(propulsionTypes);
    }

    public PropulsionType getPrimaryTransitPropulsionType() {
        return primaryTransitPropulsionType;
    }

    public void setPrimaryTransitPropulsionType(PropulsionType primaryTransitPropulsionType) {
        this.primaryTransitPropulsionType = primaryTransitPropulsionType == null
                ? PropulsionType.NONE
                : primaryTransitPropulsionType;
    }

    public List<BoatMotor> getMotors() {
        return motors;
    }

    public void setMotors(List<BoatMotor> motors) {
        this.motors = motors == null ? new ArrayList<>() : new ArrayList<>(motors);
    }

    public String getConfigurationDescription() {
        return configurationDescription;
    }

    public void setConfigurationDescription(String configurationDescription) {
        this.configurationDescription = configurationDescription;
    }

    public BigDecimal getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public void setMaxSpeedKmh(BigDecimal maxSpeedKmh) {
        this.maxSpeedKmh = maxSpeedKmh;
    }

    public BigDecimal getMeasuredCruiseSpeedKmh() {
        return measuredCruiseSpeedKmh;
    }

    public void setMeasuredCruiseSpeedKmh(BigDecimal measuredCruiseSpeedKmh) {
        this.measuredCruiseSpeedKmh = measuredCruiseSpeedKmh;
    }

    public BigDecimal getComfortableRoundTripRangeKm() {
        return comfortableRoundTripRangeKm;
    }

    public void setComfortableRoundTripRangeKm(BigDecimal comfortableRoundTripRangeKm) {
        this.comfortableRoundTripRangeKm = comfortableRoundTripRangeKm;
    }

    public WindWaveCapability getWindWaveOverride() {
        return windWaveOverride;
    }

    public void setWindWaveOverride(WindWaveCapability windWaveOverride) {
        this.windWaveOverride = windWaveOverride;
    }

    public String getFreeTextHash() {
        return freeTextHash;
    }

    public void setFreeTextHash(String freeTextHash) {
        this.freeTextHash = freeTextHash;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSystemGenerated() {
        return systemGenerated;
    }

    public void setSystemGenerated(boolean systemGenerated) {
        this.systemGenerated = systemGenerated;
    }

    public BoatProvenance getProvenance() {
        return provenance;
    }

    public void setProvenance(BoatProvenance provenance) {
        this.provenance = provenance;
    }

    public BoatMotor primaryTransitMotor() {
        PropulsionType primary = primaryTransitPropulsionType;
        if (primary == null || motors == null) {
            return null;
        }
        return motors.stream()
                .filter(motor -> motor != null && motor.propulsionType() == primary)
                .findFirst()
                .orElse(null);
    }
}
