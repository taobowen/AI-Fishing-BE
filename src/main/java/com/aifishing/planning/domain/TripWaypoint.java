package com.aifishing.planning.domain;

import com.aifishing.lake.processing.dto.FeatureType;
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
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import com.aifishing.planning.spatial.TargetKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trip_waypoints")
public class TripWaypoint {

    @Id
    private UUID id;

    @Column(name = "trip_plan_id", nullable = false)
    private UUID tripPlanId;

    @Column(nullable = false)
    private int sequence;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "planned_arrival_time")
    private LocalTime plannedArrivalTime;

    @Column(name = "planned_departure_time")
    private LocalTime plannedDepartureTime;

    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;

    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;

    @Column(name = "planned_dwell_minutes")
    private Integer plannedDwellMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type")
    private FeatureType featureType;

    @Column(name = "min_depth_m")
    private BigDecimal minDepthM;

    @Column(name = "max_depth_m")
    private BigDecimal maxDepthM;

    @Column(name = "recommended_technique")
    private String recommendedTechnique;

    private String reason;

    @Column(name = "lake_feature_id")
    private UUID lakeFeatureId;

    @Column(name = "representative_depth_m")
    private BigDecimal representativeDepthM;

    @Column(name = "candidate_score")
    private BigDecimal candidateScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "jsonb")
    private Map<String, Object> scoreBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_techniques", columnDefinition = "jsonb")
    private List<String> recommendedTechniques;

    @Column(name = "estimated_travel_distance_from_previous_m")
    private BigDecimal estimatedTravelDistanceFromPreviousM;

    @Column(name = "estimated_travel_minutes_from_previous")
    private BigDecimal estimatedTravelMinutesFromPrevious;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> environment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "why_this_time", columnDefinition = "jsonb")
    private List<String> whyThisTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind")
    private TargetKind targetKind;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "target_geometry", columnDefinition = "geometry(Geometry,4326)")
    private Geometry targetGeometry;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "fishing_corridor", columnDefinition = "geometry(Geometry,4326)")
    private Geometry fishingCorridor;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "entry_point", columnDefinition = "geometry(Point,4326)")
    private Point entryPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "exit_point", columnDefinition = "geometry(Point,4326)")
    private Point exitPoint;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "selected_fishing_path", columnDefinition = "geometry(LineString,4326)")
    private LineString selectedFishingPath;

    @Column(name = "fishing_corridor_width_m")
    private BigDecimal fishingCorridorWidthM;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "fishing_target_id")
    private UUID fishingTargetId;

    @Column(name = "planned_visit_minutes")
    private Integer plannedVisitMinutes;

    @Column(name = "planned_fishing_minutes")
    private Integer plannedFishingMinutes;

    @Column(name = "planned_internal_transit_minutes")
    private Integer plannedInternalTransitMinutes;

    @Column(name = "planned_wait_minutes")
    private Integer plannedWaitMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_kind")
    private TargetKind visitKind;

    @Column(name = "visit_scope_id")
    private UUID visitScopeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visit_scope_member_ids", columnDefinition = "jsonb")
    private List<String> visitScopeMemberIds;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "visit_envelope", columnDefinition = "geometry(Geometry,4326)")
    private Geometry visitEnvelope;

    @Column(name = "closed_loop")
    private Boolean closedLoop;

    @Column(name = "traversal_key")
    private String traversalKey;

    @Column(name = "spatial_planning_snapshot_id")
    private UUID spatialPlanningSnapshotId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> tactical;

    @jakarta.persistence.Transient
    private com.aifishing.planning.spatial.ZoneSubPlan pendingSubPlan;

    @jakarta.persistence.Transient
    private Map<UUID, Map<String, Object>> pendingChildTactical;

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

    public UUID getTripPlanId() {
        return tripPlanId;
    }

    public void setTripPlanId(UUID tripPlanId) {
        this.tripPlanId = tripPlanId;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public LocalTime getPlannedArrivalTime() {
        return plannedArrivalTime;
    }

    public void setPlannedArrivalTime(LocalTime plannedArrivalTime) {
        this.plannedArrivalTime = plannedArrivalTime;
    }

    public LocalTime getPlannedDepartureTime() {
        return plannedDepartureTime;
    }

    public void setPlannedDepartureTime(LocalTime plannedDepartureTime) {
        this.plannedDepartureTime = plannedDepartureTime;
    }

    public Instant getPlannedArrivalAt() {
        return plannedArrivalAt;
    }

    public void setPlannedArrivalAt(Instant plannedArrivalAt) {
        this.plannedArrivalAt = plannedArrivalAt;
    }

    public Instant getPlannedDepartureAt() {
        return plannedDepartureAt;
    }

    public void setPlannedDepartureAt(Instant plannedDepartureAt) {
        this.plannedDepartureAt = plannedDepartureAt;
    }

    public Integer getPlannedDwellMinutes() {
        return plannedDwellMinutes;
    }

    public void setPlannedDwellMinutes(Integer plannedDwellMinutes) {
        this.plannedDwellMinutes = plannedDwellMinutes;
    }

    public FeatureType getFeatureType() {
        return featureType;
    }

    public void setFeatureType(FeatureType featureType) {
        this.featureType = featureType;
    }

    public BigDecimal getMinDepthM() {
        return minDepthM;
    }

    public void setMinDepthM(BigDecimal minDepthM) {
        this.minDepthM = minDepthM;
    }

    public BigDecimal getMaxDepthM() {
        return maxDepthM;
    }

    public void setMaxDepthM(BigDecimal maxDepthM) {
        this.maxDepthM = maxDepthM;
    }

    public String getRecommendedTechnique() {
        return recommendedTechnique;
    }

    public void setRecommendedTechnique(String recommendedTechnique) {
        this.recommendedTechnique = recommendedTechnique;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getLakeFeatureId() {
        return lakeFeatureId;
    }

    public void setLakeFeatureId(UUID lakeFeatureId) {
        this.lakeFeatureId = lakeFeatureId;
    }

    public BigDecimal getRepresentativeDepthM() {
        return representativeDepthM;
    }

    public void setRepresentativeDepthM(BigDecimal representativeDepthM) {
        this.representativeDepthM = representativeDepthM;
    }

    public BigDecimal getCandidateScore() {
        return candidateScore;
    }

    public void setCandidateScore(BigDecimal candidateScore) {
        this.candidateScore = candidateScore;
    }

    public Map<String, Object> getScoreBreakdown() {
        return scoreBreakdown;
    }

    public void setScoreBreakdown(Map<String, Object> scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown;
    }

    public List<String> getRecommendedTechniques() {
        return recommendedTechniques;
    }

    public void setRecommendedTechniques(List<String> recommendedTechniques) {
        this.recommendedTechniques = recommendedTechniques;
    }

    public BigDecimal getEstimatedTravelDistanceFromPreviousM() {
        return estimatedTravelDistanceFromPreviousM;
    }

    public void setEstimatedTravelDistanceFromPreviousM(BigDecimal estimatedTravelDistanceFromPreviousM) {
        this.estimatedTravelDistanceFromPreviousM = estimatedTravelDistanceFromPreviousM;
    }

    public BigDecimal getEstimatedTravelMinutesFromPrevious() {
        return estimatedTravelMinutesFromPrevious;
    }

    public void setEstimatedTravelMinutesFromPrevious(BigDecimal estimatedTravelMinutesFromPrevious) {
        this.estimatedTravelMinutesFromPrevious = estimatedTravelMinutesFromPrevious;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, Object> environment) {
        this.environment = environment;
    }

    public List<String> getWhyThisTime() {
        return whyThisTime;
    }

    public void setWhyThisTime(List<String> whyThisTime) {
        this.whyThisTime = whyThisTime;
    }

    public TargetKind getTargetKind() {
        return targetKind;
    }

    public void setTargetKind(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public Geometry getTargetGeometry() {
        return targetGeometry;
    }

    public void setTargetGeometry(Geometry targetGeometry) {
        this.targetGeometry = targetGeometry;
    }

    public Geometry getFishingCorridor() {
        return fishingCorridor;
    }

    public void setFishingCorridor(Geometry fishingCorridor) {
        this.fishingCorridor = fishingCorridor;
    }

    public Point getEntryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(Point entryPoint) {
        this.entryPoint = entryPoint;
    }

    public Point getExitPoint() {
        return exitPoint;
    }

    public void setExitPoint(Point exitPoint) {
        this.exitPoint = exitPoint;
    }

    public LineString getSelectedFishingPath() {
        return selectedFishingPath;
    }

    public void setSelectedFishingPath(LineString selectedFishingPath) {
        this.selectedFishingPath = selectedFishingPath;
    }

    public BigDecimal getFishingCorridorWidthM() {
        return fishingCorridorWidthM;
    }

    public void setFishingCorridorWidthM(BigDecimal fishingCorridorWidthM) {
        this.fishingCorridorWidthM = fishingCorridorWidthM;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public Integer getPlannedVisitMinutes() {
        return plannedVisitMinutes;
    }

    public void setPlannedVisitMinutes(Integer plannedVisitMinutes) {
        this.plannedVisitMinutes = plannedVisitMinutes;
    }

    public Integer getPlannedFishingMinutes() {
        return plannedFishingMinutes;
    }

    public void setPlannedFishingMinutes(Integer plannedFishingMinutes) {
        this.plannedFishingMinutes = plannedFishingMinutes;
    }

    public Integer getPlannedInternalTransitMinutes() {
        return plannedInternalTransitMinutes;
    }

    public void setPlannedInternalTransitMinutes(Integer plannedInternalTransitMinutes) {
        this.plannedInternalTransitMinutes = plannedInternalTransitMinutes;
    }

    public Integer getPlannedWaitMinutes() {
        return plannedWaitMinutes;
    }

    public void setPlannedWaitMinutes(Integer plannedWaitMinutes) {
        this.plannedWaitMinutes = plannedWaitMinutes;
    }

    public TargetKind getVisitKind() {
        return visitKind;
    }

    public void setVisitKind(TargetKind visitKind) {
        this.visitKind = visitKind;
    }

    public UUID getVisitScopeId() {
        return visitScopeId;
    }

    public void setVisitScopeId(UUID visitScopeId) {
        this.visitScopeId = visitScopeId;
    }

    public List<String> getVisitScopeMemberIds() {
        return visitScopeMemberIds;
    }

    public void setVisitScopeMemberIds(List<String> visitScopeMemberIds) {
        this.visitScopeMemberIds = visitScopeMemberIds;
    }

    public Geometry getVisitEnvelope() {
        return visitEnvelope;
    }

    public void setVisitEnvelope(Geometry visitEnvelope) {
        this.visitEnvelope = visitEnvelope;
    }

    public Boolean getClosedLoop() {
        return closedLoop;
    }

    public void setClosedLoop(Boolean closedLoop) {
        this.closedLoop = closedLoop;
    }

    public String getTraversalKey() {
        return traversalKey;
    }

    public void setTraversalKey(String traversalKey) {
        this.traversalKey = traversalKey;
    }

    public UUID getSpatialPlanningSnapshotId() {
        return spatialPlanningSnapshotId;
    }

    public void setSpatialPlanningSnapshotId(UUID spatialPlanningSnapshotId) {
        this.spatialPlanningSnapshotId = spatialPlanningSnapshotId;
    }

    public com.aifishing.planning.spatial.ZoneSubPlan getPendingSubPlan() {
        return pendingSubPlan;
    }

    public void setPendingSubPlan(com.aifishing.planning.spatial.ZoneSubPlan pendingSubPlan) {
        this.pendingSubPlan = pendingSubPlan;
    }

    public Map<String, Object> getTactical() {
        return tactical;
    }

    public void setTactical(Map<String, Object> tactical) {
        this.tactical = tactical;
    }

    public Map<UUID, Map<String, Object>> getPendingChildTactical() {
        return pendingChildTactical;
    }

    public void setPendingChildTactical(Map<UUID, Map<String, Object>> pendingChildTactical) {
        this.pendingChildTactical = pendingChildTactical;
    }

    public TargetKind resolvedTargetKind() {
        return targetKind == null ? TargetKind.POINT : targetKind;
    }

    public Geometry resolvedTargetGeometry() {
        return targetGeometry != null ? targetGeometry : location;
    }

    public Point resolvedEntryPoint() {
        return entryPoint != null ? entryPoint : location;
    }

    public Point resolvedExitPoint() {
        return exitPoint != null ? exitPoint : location;
    }

    public int resolvedVisitMinutes() {
        if (plannedVisitMinutes != null) {
            return plannedVisitMinutes;
        }
        return plannedDwellMinutes == null ? 0 : plannedDwellMinutes;
    }
}
