package com.aifishing.lake.domain;

import com.aifishing.common.domain.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lakes")
public class Lake extends AuditedEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_lake_id")
    private String sourceLakeId;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point centroid;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(MultiPolygon,4326)")
    private MultiPolygon boundary;

    @Column(name = "mean_depth_m")
    private BigDecimal meanDepthM;

    @Column(name = "max_depth_m")
    private BigDecimal maxDepthM;

    @Column(name = "time_zone_id", nullable = false)
    private String timeZoneId;

    private Long ogfId;

    @Column(name = "waterbody_lid")
    private String waterbodyLid;

    @Column(name = "official_name")
    private String officialName;

    private String municipality;

    @Column(name = "bbox_min_lng")
    private Double bboxMinLng;

    @Column(name = "bbox_min_lat")
    private Double bboxMinLat;

    @Column(name = "bbox_max_lng")
    private Double bboxMaxLng;

    @Column(name = "bbox_max_lat")
    private Double bboxMaxLat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "identity_metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> identityMetadata;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "current_analysis_version")
    private String currentAnalysisVersion;

    @Column(name = "processing_error")
    private String processingError;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceLakeId() {
        return sourceLakeId;
    }

    public void setSourceLakeId(String sourceLakeId) {
        this.sourceLakeId = sourceLakeId;
    }

    public Point getCentroid() {
        return centroid;
    }

    public void setCentroid(Point centroid) {
        this.centroid = centroid;
    }

    public MultiPolygon getBoundary() {
        return boundary;
    }

    public void setBoundary(MultiPolygon boundary) {
        this.boundary = boundary;
    }

    public BigDecimal getMeanDepthM() {
        return meanDepthM;
    }

    public void setMeanDepthM(BigDecimal meanDepthM) {
        this.meanDepthM = meanDepthM;
    }

    public BigDecimal getMaxDepthM() {
        return maxDepthM;
    }

    public void setMaxDepthM(BigDecimal maxDepthM) {
        this.maxDepthM = maxDepthM;
    }

    public String getTimeZoneId() {
        return timeZoneId;
    }

    public void setTimeZoneId(String timeZoneId) {
        this.timeZoneId = timeZoneId;
    }

    public Long getOgfId() {
        return ogfId;
    }

    public void setOgfId(Long ogfId) {
        this.ogfId = ogfId;
    }

    public String getWaterbodyLid() {
        return waterbodyLid;
    }

    public void setWaterbodyLid(String waterbodyLid) {
        this.waterbodyLid = waterbodyLid;
    }

    public String getOfficialName() {
        return officialName;
    }

    public void setOfficialName(String officialName) {
        this.officialName = officialName;
    }

    public String getMunicipality() {
        return municipality;
    }

    public void setMunicipality(String municipality) {
        this.municipality = municipality;
    }

    public Double getBboxMinLng() {
        return bboxMinLng;
    }

    public void setBboxMinLng(Double bboxMinLng) {
        this.bboxMinLng = bboxMinLng;
    }

    public Double getBboxMinLat() {
        return bboxMinLat;
    }

    public void setBboxMinLat(Double bboxMinLat) {
        this.bboxMinLat = bboxMinLat;
    }

    public Double getBboxMaxLng() {
        return bboxMaxLng;
    }

    public void setBboxMaxLng(Double bboxMaxLng) {
        this.bboxMaxLng = bboxMaxLng;
    }

    public Double getBboxMaxLat() {
        return bboxMaxLat;
    }

    public void setBboxMaxLat(Double bboxMaxLat) {
        this.bboxMaxLat = bboxMaxLat;
    }

    public java.util.Map<String, Object> getIdentityMetadata() {
        return identityMetadata;
    }

    public void setIdentityMetadata(java.util.Map<String, Object> identityMetadata) {
        this.identityMetadata = identityMetadata;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getCurrentAnalysisVersion() {
        return currentAnalysisVersion;
    }

    public void setCurrentAnalysisVersion(String currentAnalysisVersion) {
        this.currentAnalysisVersion = currentAnalysisVersion;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }
}
