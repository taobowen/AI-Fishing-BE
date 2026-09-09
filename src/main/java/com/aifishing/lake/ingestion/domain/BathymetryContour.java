package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiLineString;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bathymetry_contours")
public class BathymetryContour extends CanonicalOntarioRecord {

    @Column(name = "depth_m")
    private BigDecimal depthM;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(MultiLineString,4326)")
    private MultiLineString geometry;

    @Column(name = "survey_date")
    private LocalDate surveyDate;

    @Column(name = "survey_method")
    private String surveyMethod;

    @Column(name = "horizontal_accuracy")
    private String horizontalAccuracy;

    public BigDecimal getDepthM() {
        return depthM;
    }

    public void setDepthM(BigDecimal depthM) {
        this.depthM = depthM;
    }

    public MultiLineString getGeometry() {
        return geometry;
    }

    public void setGeometry(MultiLineString geometry) {
        this.geometry = geometry;
    }

    public LocalDate getSurveyDate() {
        return surveyDate;
    }

    public void setSurveyDate(LocalDate surveyDate) {
        this.surveyDate = surveyDate;
    }

    public String getSurveyMethod() {
        return surveyMethod;
    }

    public void setSurveyMethod(String surveyMethod) {
        this.surveyMethod = surveyMethod;
    }

    public String getHorizontalAccuracy() {
        return horizontalAccuracy;
    }

    public void setHorizontalAccuracy(String horizontalAccuracy) {
        this.horizontalAccuracy = horizontalAccuracy;
    }
}
