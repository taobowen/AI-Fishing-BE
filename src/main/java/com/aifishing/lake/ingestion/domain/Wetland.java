package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "wetlands")
public class Wetland extends CanonicalOntarioRecord {

    @Column(name = "wetland_type")
    private String wetlandType;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    public String getWetlandType() {
        return wetlandType;
    }

    public void setWetlandType(String wetlandType) {
        this.wetlandType = wetlandType;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }
}
