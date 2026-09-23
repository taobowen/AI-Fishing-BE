package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.EvalCaseResultStatus;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "guidance_eval_case_results")
public class GuidanceEvalCaseResultEntity extends GuidanceCreatedEntity {

    @Column(name = "eval_run_id", nullable = false)
    private UUID evalRunId;

    @Column(name = "case_id", nullable = false, length = 128)
    private String caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EvalCaseResultStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> allowedActions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forbidden_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> forbiddenActions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> invariants;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actual_decision", columnDefinition = "jsonb")
    private Map<String, Object> actualDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_primary_action", length = 32)
    private GuidanceAction actualPrimaryAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_primary_action", length = 32)
    private GuidanceAction recordedPrimaryAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorded_outcome_kind", length = 16)
    private OutcomeKind recordedOutcomeKind;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "scenario_id", length = 128)
    private String scenarioId;

    private Long seed;

    public UUID getEvalRunId() {
        return evalRunId;
    }

    public void setEvalRunId(UUID evalRunId) {
        this.evalRunId = evalRunId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public EvalCaseResultStatus getStatus() {
        return status;
    }

    public void setStatus(EvalCaseResultStatus status) {
        this.status = status;
    }

    public List<String> getAllowedActions() {
        return allowedActions;
    }

    public void setAllowedActions(List<String> allowedActions) {
        this.allowedActions = allowedActions;
    }

    public List<String> getForbiddenActions() {
        return forbiddenActions;
    }

    public void setForbiddenActions(List<String> forbiddenActions) {
        this.forbiddenActions = forbiddenActions;
    }

    public List<String> getInvariants() {
        return invariants;
    }

    public void setInvariants(List<String> invariants) {
        this.invariants = invariants;
    }

    public Map<String, Object> getActualDecision() {
        return actualDecision;
    }

    public void setActualDecision(Map<String, Object> actualDecision) {
        this.actualDecision = actualDecision;
    }

    public GuidanceAction getActualPrimaryAction() {
        return actualPrimaryAction;
    }

    public void setActualPrimaryAction(GuidanceAction actualPrimaryAction) {
        this.actualPrimaryAction = actualPrimaryAction;
    }

    public GuidanceAction getRecordedPrimaryAction() {
        return recordedPrimaryAction;
    }

    public void setRecordedPrimaryAction(GuidanceAction recordedPrimaryAction) {
        this.recordedPrimaryAction = recordedPrimaryAction;
    }

    public OutcomeKind getRecordedOutcomeKind() {
        return recordedOutcomeKind;
    }

    public void setRecordedOutcomeKind(OutcomeKind recordedOutcomeKind) {
        this.recordedOutcomeKind = recordedOutcomeKind;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}
