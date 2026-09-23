package com.aifishing.trip.domain;

import com.aifishing.common.domain.AuditedEntity;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.TripStatus;
import com.aifishing.common.jpa.LakeLocalTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip extends AuditedEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_target_species", nullable = false)
    private FishSpecies primaryTargetSpecies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_target_species", nullable = false, columnDefinition = "jsonb")
    private List<FishSpecies> secondaryTargetSpecies = new ArrayList<>();

    @Column(name = "planned_date", nullable = false)
    private LocalDate plannedDate;

    @Column(name = "planned_end_date")
    private LocalDate plannedEndDate;

    @Convert(converter = LakeLocalTimeConverter.class)
    @Column(name = "fishing_start_time", nullable = false)
    private LocalTime fishingStartTime;

    @Convert(converter = LakeLocalTimeConverter.class)
    @Column(name = "fishing_end_time", nullable = false)
    private LocalTime fishingEndTime;

    @Column(name = "boat_id")
    private UUID boatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fishing_mode", nullable = false)
    private FishingMode fishingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    private String notes;

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

    public UUID getLakeId() {
        return lakeId;
    }

    public void setLakeId(UUID lakeId) {
        this.lakeId = lakeId;
    }

    public FishSpecies getPrimaryTargetSpecies() {
        return primaryTargetSpecies;
    }

    public void setPrimaryTargetSpecies(FishSpecies primaryTargetSpecies) {
        this.primaryTargetSpecies = primaryTargetSpecies;
    }

    public List<FishSpecies> getSecondaryTargetSpecies() {
        return secondaryTargetSpecies;
    }

    public void setSecondaryTargetSpecies(List<FishSpecies> secondaryTargetSpecies) {
        this.secondaryTargetSpecies = secondaryTargetSpecies == null ? new ArrayList<>() : secondaryTargetSpecies;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public LocalDate getPlannedEndDate() {
        return plannedEndDate;
    }

    public void setPlannedEndDate(LocalDate plannedEndDate) {
        this.plannedEndDate = plannedEndDate;
    }

    public LocalTime getFishingStartTime() {
        return fishingStartTime;
    }

    public void setFishingStartTime(LocalTime fishingStartTime) {
        this.fishingStartTime = fishingStartTime;
    }

    public LocalTime getFishingEndTime() {
        return fishingEndTime;
    }

    public void setFishingEndTime(LocalTime fishingEndTime) {
        this.fishingEndTime = fishingEndTime;
    }

    public UUID getBoatId() {
        return boatId;
    }

    public void setBoatId(UUID boatId) {
        this.boatId = boatId;
    }

    public FishingMode getFishingMode() {
        return fishingMode;
    }

    public void setFishingMode(FishingMode fishingMode) {
        this.fishingMode = fishingMode;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
