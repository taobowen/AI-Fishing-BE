package com.aifishing.lake.ingestion.domain;

import com.aifishing.common.enums.FishSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "fish_stocking_records")
public class FishStockingRecord extends CanonicalOntarioRecord {

    @Column(name = "source_species_name")
    private String sourceSpeciesName;

    @Enumerated(EnumType.STRING)
    private FishSpecies species;

    @Column(name = "stocking_year")
    private Integer stockingYear;

    @Column(name = "stocking_date")
    private LocalDate stockingDate;

    private Integer quantity;

    @Column(name = "life_stage")
    private String lifeStage;

    public String getSourceSpeciesName() {
        return sourceSpeciesName;
    }

    public void setSourceSpeciesName(String sourceSpeciesName) {
        this.sourceSpeciesName = sourceSpeciesName;
    }

    public FishSpecies getSpecies() {
        return species;
    }

    public void setSpecies(FishSpecies species) {
        this.species = species;
    }

    public Integer getStockingYear() {
        return stockingYear;
    }

    public void setStockingYear(Integer stockingYear) {
        this.stockingYear = stockingYear;
    }

    public LocalDate getStockingDate() {
        return stockingDate;
    }

    public void setStockingDate(LocalDate stockingDate) {
        this.stockingDate = stockingDate;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getLifeStage() {
        return lifeStage;
    }

    public void setLifeStage(String lifeStage) {
        this.lifeStage = lifeStage;
    }
}
