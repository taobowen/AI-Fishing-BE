package com.aifishing.planning.domain;

import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.util.UUID;

@Entity
@Table(name = "trip_planning_input_targets")
public class TripPlanningInputTarget {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlanningInputTargetSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TemplateTargetKind kind;

    @Column(length = 128)
    private String name;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "origin_template_target_id")
    private UUID originTemplateTargetId;

    @Column(name = "origin_required_point_id")
    private UUID originRequiredPointId;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(UUID snapshotId) {
        this.snapshotId = snapshotId;
    }

    public PlanningInputTargetSource getSource() {
        return source;
    }

    public void setSource(PlanningInputTargetSource source) {
        this.source = source;
    }

    public TemplateTargetKind getKind() {
        return kind;
    }

    public void setKind(TemplateTargetKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getOriginTemplateTargetId() {
        return originTemplateTargetId;
    }

    public void setOriginTemplateTargetId(UUID originTemplateTargetId) {
        this.originTemplateTargetId = originTemplateTargetId;
    }

    public UUID getOriginRequiredPointId() {
        return originRequiredPointId;
    }

    public void setOriginRequiredPointId(UUID originRequiredPointId) {
        this.originRequiredPointId = originRequiredPointId;
    }
}
