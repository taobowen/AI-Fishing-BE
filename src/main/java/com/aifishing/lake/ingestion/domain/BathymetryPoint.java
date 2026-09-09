package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bathymetry_points")
public class BathymetryPoint extends CanonicalOntarioRecord {

    @Column(name = "depth_m")
    private BigDecimal depthM;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "survey_date")
    private LocalDate surveyDate;

    @Column(name = "survey_method")
    private String surveyMethod;

    private String accuracy;

    public BigDecimal getDepthM() {
        return depthM;
    }

    public void setDepthM(BigDecimal depthM) {
        this.depthM = depthM;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
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

    public String getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(String accuracy) {
        this.accuracy = accuracy;
    }
}
