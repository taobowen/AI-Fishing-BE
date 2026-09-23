package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.ReplayMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "guidance_eval_runs")
public class GuidanceEvalRunEntity extends GuidanceCreatedEntity {

    @Column(nullable = false, length = 128)
    private String suite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvalSuiteKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "replay_mode", nullable = false, length = 32)
    private ReplayMode replayMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recomputed_components", nullable = false, columnDefinition = "jsonb")
    private List<String> recomputedComponents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_versions", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> componentVersions;

    @Column(name = "pricing_version", length = 64)
    private String pricingVersion;

    @Column(name = "loaded_cases", nullable = false)
    private int loadedCases;

    @Column(name = "skipped_cases", nullable = false)
    private int skippedCases;

    @Column(name = "attempted_cases", nullable = false)
    private int attemptedCases;

    @Column(name = "passed_cases")
    private Integer passedCases;

    @Column(name = "eval_coverage", precision = 4, scale = 3)
    private BigDecimal evalCoverage;

    @Column(name = "scenario_id", length = 128)
    private String scenarioId;

    private Long seed;

    @Column(name = "simulation_version", length = 64)
    private String simulationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenario_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> scenarioTags;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public String getSuite() {
        return suite;
    }

    public void setSuite(String suite) {
        this.suite = suite;
    }

    public EvalSuiteKind getKind() {
        return kind;
    }

    public void setKind(EvalSuiteKind kind) {
        this.kind = kind;
    }

    public ReplayMode getReplayMode() {
        return replayMode;
    }

    public void setReplayMode(ReplayMode replayMode) {
        this.replayMode = replayMode;
    }

    public List<String> getRecomputedComponents() {
        return recomputedComponents;
    }

    public void setRecomputedComponents(List<String> recomputedComponents) {
        this.recomputedComponents = recomputedComponents;
    }

    public Map<String, Object> getComponentVersions() {
        return componentVersions;
    }

    public void setComponentVersions(Map<String, Object> componentVersions) {
        this.componentVersions = componentVersions;
    }

    public String getPricingVersion() {
        return pricingVersion;
    }

    public void setPricingVersion(String pricingVersion) {
        this.pricingVersion = pricingVersion;
    }

    public int getLoadedCases() {
        return loadedCases;
    }

    public void setLoadedCases(int loadedCases) {
        this.loadedCases = loadedCases;
    }

    public int getSkippedCases() {
        return skippedCases;
    }

    public void setSkippedCases(int skippedCases) {
        this.skippedCases = skippedCases;
    }

    public int getAttemptedCases() {
        return attemptedCases;
    }

    public void setAttemptedCases(int attemptedCases) {
        this.attemptedCases = attemptedCases;
    }

    public Integer getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(Integer passedCases) {
        this.passedCases = passedCases;
    }

    public BigDecimal getEvalCoverage() {
        return evalCoverage;
    }

    public void setEvalCoverage(BigDecimal evalCoverage) {
        this.evalCoverage = evalCoverage;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getSimulationVersion() {
        return simulationVersion;
    }

    public void setSimulationVersion(String simulationVersion) {
        this.simulationVersion = simulationVersion;
    }

    public List<String> getScenarioTags() {
        return scenarioTags;
    }

    public void setScenarioTags(List<String> scenarioTags) {
        this.scenarioTags = scenarioTags;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
