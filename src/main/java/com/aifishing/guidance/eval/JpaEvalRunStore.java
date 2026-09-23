package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.DeliveredDecision;
import com.aifishing.guidance.contracts.EvalCaseResult;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.EvalCoverage;
import com.aifishing.guidance.contracts.EvalRun;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.RecomputedComponent;
import com.aifishing.guidance.persistence.GuidanceEvalCaseResultEntity;
import com.aifishing.guidance.persistence.GuidanceEvalCaseResultRepository;
import com.aifishing.guidance.persistence.GuidanceEvalRunEntity;
import com.aifishing.guidance.persistence.GuidanceEvalRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaEvalRunStore implements EvalRunStore {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final GuidanceEvalRunRepository runRepository;
    private final GuidanceEvalCaseResultRepository resultRepository;

    public JpaEvalRunStore(
            GuidanceEvalRunRepository runRepository,
            GuidanceEvalCaseResultRepository resultRepository
    ) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    @Override
    @Transactional
    public void save(EvalRun run, List<EvalCaseResult> caseResults) {
        if (run == null || run.evalRunId() == null) {
            throw new IllegalArgumentException("eval run is required");
        }
        GuidanceEvalRunEntity entity = runRepository.findById(run.evalRunId()).orElseGet(GuidanceEvalRunEntity::new);
        entity.setId(run.evalRunId());
        entity.setSchemaVersion(GuidanceSchemaVersion.VALUE);
        entity.setSuite(run.suite());
        entity.setKind(run.kind());
        entity.setReplayMode(run.replayMode());
        entity.setRecomputedComponents(run.recomputedComponents().stream().map(Enum::name).toList());
        entity.setComponentVersions(GuidanceContracts.mapper().convertValue(run.componentVersions(), MAP));
        entity.setPricingVersion(run.pricingVersion());
        EvalCoverage coverage = run.coverage();
        entity.setLoadedCases(coverage == null ? 0 : coverage.loadedCases());
        entity.setSkippedCases(coverage == null ? 0 : coverage.skippedCases());
        entity.setAttemptedCases(coverage == null ? 0 : coverage.attemptedCases());
        entity.setPassedCases(coverage == null ? null : coverage.passedCases());
        entity.setEvalCoverage(coverage == null || coverage.evalCoverage() == null
                ? null
                : BigDecimal.valueOf(coverage.evalCoverage()).setScale(3, RoundingMode.HALF_UP));
        entity.setScenarioId(run.scenarioId());
        entity.setSeed(run.seed());
        entity.setSimulationVersion(run.simulationVersion());
        entity.setScenarioTags(run.scenarioTags() == null ? List.of() : run.scenarioTags());
        entity.setStartedAt(run.startedAt());
        entity.setFinishedAt(run.finishedAt());
        runRepository.save(entity);

        resultRepository.deleteAll(resultRepository.findByEvalRunIdOrderByCreatedAtAsc(run.evalRunId()));
        if (caseResults == null) {
            return;
        }
        for (EvalCaseResult result : caseResults) {
            GuidanceEvalCaseResultEntity row = new GuidanceEvalCaseResultEntity();
            row.setId(UUID.randomUUID());
            row.setSchemaVersion(GuidanceSchemaVersion.VALUE);
            row.setEvalRunId(run.evalRunId());
            row.setCaseId(result.caseId());
            row.setStatus(result.status());
            row.setAllowedActions(result.allowedActions().stream().map(Enum::name).toList());
            row.setForbiddenActions(result.forbiddenActions().stream().map(Enum::name).toList());
            row.setInvariants(result.invariants());
            row.setActualDecision(result.actualDecision() == null
                    ? null
                    : GuidanceContracts.mapper().convertValue(result.actualDecision(), MAP));
            row.setActualPrimaryAction(result.actualPrimaryAction());
            row.setRecordedPrimaryAction(result.recordedPrimaryAction());
            row.setRecordedOutcomeKind(result.recordedOutcomeKind());
            row.setSkipReason(result.skipReason());
            row.setErrorMessage(result.errorMessage());
            row.setScenarioId(result.scenarioId());
            row.setSeed(result.seed());
            resultRepository.save(row);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvalRun> find(UUID evalRunId) {
        return runRepository.findById(evalRunId).map(JpaEvalRunStore::toRun);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvalCaseResult> findResults(UUID evalRunId) {
        List<EvalCaseResult> results = new ArrayList<>();
        for (GuidanceEvalCaseResultEntity row : resultRepository.findByEvalRunIdOrderByCreatedAtAsc(evalRunId)) {
            results.add(toResult(row));
        }
        return List.copyOf(results);
    }

    private static EvalRun toRun(GuidanceEvalRunEntity entity) {
        List<RecomputedComponent> recomputed = entity.getRecomputedComponents() == null
                ? List.of()
                : entity.getRecomputedComponents().stream().map(RecomputedComponent::valueOf).toList();
        EvalComponentVersions versions = entity.getComponentVersions() == null
                ? EvalComponentVersions.empty()
                : GuidanceContracts.mapper().convertValue(entity.getComponentVersions(), EvalComponentVersions.class);
        Double coverage = entity.getEvalCoverage() == null ? null : entity.getEvalCoverage().doubleValue();
        return new EvalRun(
                GuidanceSchemaVersion.VALUE,
                entity.getId(),
                entity.getSuite(),
                entity.getKind(),
                entity.getReplayMode(),
                recomputed,
                versions,
                entity.getPricingVersion(),
                new EvalCoverage(
                        entity.getLoadedCases(),
                        entity.getSkippedCases(),
                        entity.getAttemptedCases(),
                        entity.getPassedCases(),
                        coverage
                ),
                entity.getScenarioId(),
                entity.getSeed(),
                entity.getSimulationVersion(),
                entity.getScenarioTags(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    private static EvalCaseResult toResult(GuidanceEvalCaseResultEntity row) {
        DeliveredDecision decision = row.getActualDecision() == null
                ? null
                : GuidanceContracts.mapper().convertValue(row.getActualDecision(), DeliveredDecision.class);
        return new EvalCaseResult(
                GuidanceSchemaVersion.VALUE,
                row.getEvalRunId(),
                row.getCaseId(),
                row.getStatus(),
                enums(row.getAllowedActions(), com.aifishing.guidance.contracts.GuidanceAction.class),
                enums(row.getForbiddenActions(), com.aifishing.guidance.contracts.GuidanceAction.class),
                row.getInvariants() == null ? List.of() : row.getInvariants(),
                decision,
                row.getActualPrimaryAction(),
                row.getRecordedPrimaryAction(),
                row.getRecordedOutcomeKind(),
                row.getSkipReason(),
                row.getErrorMessage(),
                row.getScenarioId(),
                row.getSeed()
        );
    }

    private static <E extends Enum<E>> List<E> enums(List<String> raw, Class<E> type) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(value -> Enum.valueOf(type, value)).toList();
    }
}
