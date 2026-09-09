package com.aifishing.planning.domain;

import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.lake.processing.dto.Pipeline;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trip_plans")
public class TripPlan {

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripPlanStatus status;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "strategy_run_id")
    private UUID strategyRunId;

    @Column(name = "planning_run_id")
    private UUID planningRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_pipeline")
    private Pipeline featurePipeline;

    @Column(name = "feature_analysis_version")
    private String featureAnalysisVersion;

    @Column(name = "planning_algorithm_version")
    private String planningAlgorithmVersion;

    @Column(name = "overall_plan_confidence")
    private BigDecimal overallPlanConfidence;

    @Column(name = "total_estimated_travel_distance_m")
    private BigDecimal totalEstimatedTravelDistanceM;

    @Column(name = "total_estimated_travel_minutes")
    private BigDecimal totalEstimatedTravelMinutes;

    @Column(name = "planned_launch_departure_at")
    private Instant plannedLaunchDepartureAt;

    @Column(name = "planned_return_at")
    private Instant plannedReturnAt;

    @Column(name = "total_fishing_minutes")
    private BigDecimal totalFishingMinutes;

    @Column(name = "total_planned_minutes")
    private BigDecimal totalPlannedMinutes;

    @Column(name = "schedule_reserve_minutes")
    private Integer scheduleReserveMinutes;

    @Column(name = "total_wait_minutes")
    private Integer totalWaitMinutes;

    @Column(name = "schedule_algorithm_version")
    private String scheduleAlgorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> scoreSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schedule_events", columnDefinition = "jsonb")
    private List<Map<String, Object>> scheduleEvents;

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

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public TripPlanStatus getStatus() {
        return status;
    }

    public void setStatus(TripPlanStatus status) {
        this.status = status;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public UUID getStrategyRunId() {
        return strategyRunId;
    }

    public void setStrategyRunId(UUID strategyRunId) {
        this.strategyRunId = strategyRunId;
    }

    public UUID getPlanningRunId() {
        return planningRunId;
    }

    public void setPlanningRunId(UUID planningRunId) {
        this.planningRunId = planningRunId;
    }

    public Pipeline getFeaturePipeline() {
        return featurePipeline;
    }

    public void setFeaturePipeline(Pipeline featurePipeline) {
        this.featurePipeline = featurePipeline;
    }

    public String getFeatureAnalysisVersion() {
        return featureAnalysisVersion;
    }

    public void setFeatureAnalysisVersion(String featureAnalysisVersion) {
        this.featureAnalysisVersion = featureAnalysisVersion;
    }

    public String getPlanningAlgorithmVersion() {
        return planningAlgorithmVersion;
    }

    public void setPlanningAlgorithmVersion(String planningAlgorithmVersion) {
        this.planningAlgorithmVersion = planningAlgorithmVersion;
    }

    public BigDecimal getOverallPlanConfidence() {
        return overallPlanConfidence;
    }

    public void setOverallPlanConfidence(BigDecimal overallPlanConfidence) {
        this.overallPlanConfidence = overallPlanConfidence;
    }

    public BigDecimal getTotalEstimatedTravelDistanceM() {
        return totalEstimatedTravelDistanceM;
    }

    public void setTotalEstimatedTravelDistanceM(BigDecimal totalEstimatedTravelDistanceM) {
        this.totalEstimatedTravelDistanceM = totalEstimatedTravelDistanceM;
    }

    public BigDecimal getTotalEstimatedTravelMinutes() {
        return totalEstimatedTravelMinutes;
    }

    public void setTotalEstimatedTravelMinutes(BigDecimal totalEstimatedTravelMinutes) {
        this.totalEstimatedTravelMinutes = totalEstimatedTravelMinutes;
    }

    public Instant getPlannedLaunchDepartureAt() {
        return plannedLaunchDepartureAt;
    }

    public void setPlannedLaunchDepartureAt(Instant plannedLaunchDepartureAt) {
        this.plannedLaunchDepartureAt = plannedLaunchDepartureAt;
    }

    public Instant getPlannedReturnAt() {
        return plannedReturnAt;
    }

    public void setPlannedReturnAt(Instant plannedReturnAt) {
        this.plannedReturnAt = plannedReturnAt;
    }

    public BigDecimal getTotalFishingMinutes() {
        return totalFishingMinutes;
    }

    public void setTotalFishingMinutes(BigDecimal totalFishingMinutes) {
        this.totalFishingMinutes = totalFishingMinutes;
    }

    public BigDecimal getTotalPlannedMinutes() {
        return totalPlannedMinutes;
    }

    public void setTotalPlannedMinutes(BigDecimal totalPlannedMinutes) {
        this.totalPlannedMinutes = totalPlannedMinutes;
    }

    public Integer getScheduleReserveMinutes() {
        return scheduleReserveMinutes;
    }

    public void setScheduleReserveMinutes(Integer scheduleReserveMinutes) {
        this.scheduleReserveMinutes = scheduleReserveMinutes;
    }

    public Integer getTotalWaitMinutes() {
        return totalWaitMinutes;
    }

    public void setTotalWaitMinutes(Integer totalWaitMinutes) {
        this.totalWaitMinutes = totalWaitMinutes;
    }

    public String getScheduleAlgorithmVersion() {
        return scheduleAlgorithmVersion;
    }

    public void setScheduleAlgorithmVersion(String scheduleAlgorithmVersion) {
        this.scheduleAlgorithmVersion = scheduleAlgorithmVersion;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Map<String, Object> getScoreSnapshot() {
        return scoreSnapshot;
    }

    public void setScoreSnapshot(Map<String, Object> scoreSnapshot) {
        this.scoreSnapshot = scoreSnapshot;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<Map<String, Object>> getScheduleEvents() {
        return scheduleEvents;
    }

    public void setScheduleEvents(List<Map<String, Object>> scheduleEvents) {
        this.scheduleEvents = scheduleEvents;
    }
}
