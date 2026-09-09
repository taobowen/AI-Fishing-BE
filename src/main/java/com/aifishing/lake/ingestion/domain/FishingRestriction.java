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

import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "fishing_restrictions")
public class FishingRestriction extends CanonicalOntarioRecord {

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @Enumerated(EnumType.STRING)
    private FishSpecies species;

    @Column(name = "source_species_name")
    private String sourceSpeciesName;

    @Column(name = "restriction_type")
    private String restrictionType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recurring_season", columnDefinition = "jsonb")
    private Map<String, Object> recurringSeason;

    @Column(name = "raw_text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_data", columnDefinition = "jsonb")
    private Map<String, Object> structuredData;

    @Column(name = "source_reference")
    private String sourceReference;

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public FishSpecies getSpecies() {
        return species;
    }

    public void setSpecies(FishSpecies species) {
        this.species = species;
    }

    public String getSourceSpeciesName() {
        return sourceSpeciesName;
    }

    public void setSourceSpeciesName(String sourceSpeciesName) {
        this.sourceSpeciesName = sourceSpeciesName;
    }

    public String getRestrictionType() {
        return restrictionType;
    }

    public void setRestrictionType(String restrictionType) {
        this.restrictionType = restrictionType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public Map<String, Object> getRecurringSeason() {
        return recurringSeason;
    }

    public void setRecurringSeason(Map<String, Object> recurringSeason) {
        this.recurringSeason = recurringSeason;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Map<String, Object> getStructuredData() {
        return structuredData;
    }

    public void setStructuredData(Map<String, Object> structuredData) {
        this.structuredData = structuredData;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }
}
