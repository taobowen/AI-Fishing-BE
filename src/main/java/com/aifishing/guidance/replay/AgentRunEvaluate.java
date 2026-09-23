package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EvalComponentVersions;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceAction;
import com.aifishing.guidance.contracts.OutcomeKind;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.eval.EvalCaseFixture;
import com.aifishing.guidance.eval.EvalSuiteRunner;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.eval.VersionCompareReport;
import com.aifishing.guidance.persistence.AgentCandidateDecisionEntity;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionEntity;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionEntity;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.spi.AgentRunSnapshotLoader;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Frozen evaluate of a stored run. {@link AgentRunSnapshotLoader#load} then
 * {@link EvalSuiteRunner#compare}. Writes {@code guidance_eval_runs} only —
 * never mutates the fishing session or historical agent run.
 */
@Service
public class AgentRunEvaluate {

    static final String SUITE = "admin-run-evaluate";

    private final AgentRunRepository agentRunRepository;
    private final AgentRunSnapshotLoader snapshotLoader;
    private final EvalSuiteRunner evalSuiteRunner;
    private final AgentDeliveredDecisionRepository deliveredRepository;
    private final AgentCandidateDecisionRepository candidateRepository;
    private final OutcomeAttributionRepository outcomeAttributionRepository;

    public AgentRunEvaluate(
            AgentRunRepository agentRunRepository,
            AgentRunSnapshotLoader snapshotLoader,
            EvalSuiteRunner evalSuiteRunner,
            AgentDeliveredDecisionRepository deliveredRepository,
            AgentCandidateDecisionRepository candidateRepository,
            OutcomeAttributionRepository outcomeAttributionRepository
    ) {
        this.agentRunRepository = agentRunRepository;
        this.snapshotLoader = snapshotLoader;
        this.evalSuiteRunner = evalSuiteRunner;
        this.deliveredRepository = deliveredRepository;
        this.candidateRepository = candidateRepository;
        this.outcomeAttributionRepository = outcomeAttributionRepository;
    }

    public AgentRunEvaluateResponse evaluate(UUID runId) {
        if (runId == null) {
            throw new NotFoundException("Agent run not found");
        }
        AgentRunEntity run = agentRunRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Agent run not found"));
        FrozenAgentRunSnapshot snapshot = snapshotLoader.load(runId).orElse(null);
        EvalCaseFixture fixture = fixture(run, snapshot);
        EvalSuiteRequest request = new EvalSuiteRequest(
                UUID.randomUUID(),
                SUITE,
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                versionsOf(run),
                null,
                List.of(fixture),
                runId.toString(),
                null,
                null,
                List.of("admin-replay")
        );
        VersionCompareReport report = evalSuiteRunner.compare(request);
        return AgentRunEvaluateResponse.from(runId, report);
    }

    private EvalCaseFixture fixture(AgentRunEntity run, FrozenAgentRunSnapshot snapshot) {
        Map<String, Object> recordedDecision = recordedDecision(run);
        AgentDeliveredDecisionEntity deliveredRow = deliveredRepository.findFirstByRunId(run.getId()).orElse(null);
        OutcomeKind recordedOutcome = null;
        if (deliveredRow != null) {
            List<OutcomeAttributionEntity> outcomes =
                    outcomeAttributionRepository.findByDeliveredDecisionIdOrderByAttributedAtAsc(deliveredRow.getId());
            if (!outcomes.isEmpty()) {
                recordedOutcome = outcomes.getLast().getOutcomeKind();
            }
        }
        return new EvalCaseFixture(
                "run/" + run.getId(),
                EvalSuiteKind.SHADOW_REPLAY,
                ReplayMode.FROZEN_REPLAY,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                snapshot,
                run.getId(),
                actionOf(recordedDecision),
                uuidOf(recordedDecision, "targetTripWaypointId"),
                recordedOutcome,
                run.getId().toString(),
                null,
                null
        );
    }

    private Map<String, Object> recordedDecision(AgentRunEntity run) {
        if (run.getVisibility() != AgentRunVisibility.SHADOW) {
            AgentDeliveredDecisionEntity delivered = deliveredRepository.findFirstByRunId(run.getId()).orElse(null);
            if (delivered != null && delivered.getDecision() != null && !delivered.getDecision().isEmpty()) {
                return delivered.getDecision();
            }
        }
        return candidateRepository.findFirstByRunId(run.getId())
                .map(AgentCandidateDecisionEntity::getDecision)
                .orElse(null);
    }

    private static EvalComponentVersions versionsOf(AgentRunEntity run) {
        return new EvalComponentVersions(
                run.getPromptVersion(),
                run.getModelProvider(),
                run.getModelName(),
                run.getModelVersion(),
                run.getToolSchemaVersion(),
                run.getContextVersion(),
                null,
                null,
                null,
                run.getAgentPolicyVersion(),
                run.getLearningAlgorithmVersion(),
                run.getLearningSnapshotVersion()
        );
    }

    private static GuidanceAction actionOf(Map<String, Object> decision) {
        if (decision == null) {
            return null;
        }
        Object value = decision.get("primaryAction");
        if (value == null) {
            return null;
        }
        try {
            return GuidanceAction.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static UUID uuidOf(Map<String, Object> decision, String key) {
        if (decision == null) {
            return null;
        }
        Object value = decision.get(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
