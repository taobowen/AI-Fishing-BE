package com.aifishing.lake.ingestion.domain;

import com.aifishing.common.enums.FishSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "lake_fish_species")
public class LakeFishSpecies extends CanonicalOntarioRecord {

    @Column(name = "source_species_name", nullable = false)
    private String sourceSpeciesName;

    @Enumerated(EnumType.STRING)
    private FishSpecies species;

    @Column(name = "observation_type")
    private String observationType;

    @Column(name = "observed_date")
    private LocalDate observedDate;

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

    public String getObservationType() {
        return observationType;
    }

    public void setObservationType(String observationType) {
        this.observationType = observationType;
    }

    public LocalDate getObservedDate() {
        return observedDate;
    }

    public void setObservedDate(LocalDate observedDate) {
        this.observedDate = observedDate;
    }
}
