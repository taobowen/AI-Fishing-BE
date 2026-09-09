package com.aifishing.lake.ingestion.domain;

import com.aifishing.common.enums.FishSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.util.Map;

@Entity
@Table(name = "fish_habitats")
public class FishHabitat extends CanonicalOntarioRecord {

    @Column(name = "source_species_name")
    private String sourceSpeciesName;

    @Enumerated(EnumType.STRING)
    private FishSpecies species;

    @Column(name = "habitat_type", nullable = false)
    private String habitatType;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "season_metadata", columnDefinition = "jsonb")
    private Map<String, Object> seasonMetadata;

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

    public String getHabitatType() {
        return habitatType;
    }

    public void setHabitatType(String habitatType) {
        this.habitatType = habitatType;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getSeasonMetadata() {
        return seasonMetadata;
    }

    public void setSeasonMetadata(Map<String, Object> seasonMetadata) {
        this.seasonMetadata = seasonMetadata;
    }
}
