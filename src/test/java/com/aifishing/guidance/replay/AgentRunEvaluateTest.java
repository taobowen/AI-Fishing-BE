package com.aifishing.guidance.replay;

import com.aifishing.common.exception.NotFoundException;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.AgentRunVisibility;
import com.aifishing.guidance.contracts.EvalSuiteKind;
import com.aifishing.guidance.contracts.FrozenAgentRunSnapshot;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ReplayMode;
import com.aifishing.guidance.eval.EvalFixtures;
import com.aifishing.guidance.eval.EvalSuiteRunner;
import com.aifishing.guidance.eval.EvalSuiteRunner.EvalSuiteRequest;
import com.aifishing.guidance.eval.VersionCompareReport;
import com.aifishing.guidance.eval.VersionCompareReport.OutcomeRates;
import com.aifishing.guidance.persistence.AgentCandidateDecisionRepository;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.aifishing.guidance.persistence.OutcomeAttributionRepository;
import com.aifishing.guidance.spi.AgentRunSnapshotLoader;
import com.aifishing.guidance.versions.AgentPolicyVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunEvaluateTest {

    private static final UUID RUN = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000020");
    private static final UUID EVAL = UUID.fromString("cccccccc-0003-4000-8000-000000000001");

    @Test
    void missingRunIsNotFoundAndDoesNotCompare() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunSnapshotLoader snapshots = mock(AgentRunSnapshotLoader.class);
        EvalSuiteRunner suite = mock(EvalSuiteRunner.class);
        when(runs.findById(RUN)).thenReturn(Optional.empty());

        AgentRunEvaluate evaluate = new AgentRunEvaluate(
                runs,
                snapshots,
                suite,
                mock(AgentDeliveredDecisionRepository.class),
                mock(AgentCandidateDecisionRepository.class),
                mock(OutcomeAttributionRepository.class)
        );

        assertThatThrownBy(() -> evaluate.evaluate(RUN)).isInstanceOf(NotFoundException.class);
        verify(suite, org.mockito.Mockito.never()).compare(any());
    }

    @Test
    void loadsSnapshotThenComparesFrozenReplay() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunSnapshotLoader snapshots = mock(AgentRunSnapshotLoader.class);
        EvalSuiteRunner suite = mock(EvalSuiteRunner.class);
        AgentDeliveredDecisionRepository delivered = mock(AgentDeliveredDecisionRepository.class);
        AgentCandidateDecisionRepository candidates = mock(AgentCandidateDecisionRepository.class);
        OutcomeAttributionRepository outcomes = mock(OutcomeAttributionRepository.class);

        AgentRunEntity run = new AgentRunEntity();
        run.setId(RUN);
        run.setFishingSessionId(EvalFixtures.SESSION_ID);
        run.setTrigger(GuidanceTrigger.USER_REQUEST);
        run.setStatus(AgentRunStatus.COMPLETED);
        run.setVisibility(AgentRunVisibility.PRODUCTION);
        run.setStartedAt(EvalFixtures.CLOCK);
        when(runs.findById(RUN)).thenReturn(Optional.of(run));
        FrozenAgentRunSnapshot snapshot = EvalFixtures.deterministicStay();
        when(snapshots.load(RUN)).thenReturn(Optional.of(snapshot));
        when(delivered.findFirstByRunId(RUN)).thenReturn(Optional.empty());
        when(candidates.findFirstByRunId(RUN)).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-09-18T14:00:00Z");
        when(suite.compare(any())).thenReturn(new VersionCompareReport(
                EVAL,
                List.of(AgentPolicyVersion.V1, AgentPolicyVersion.V2),
                ReplayMode.FROZEN_REPLAY,
                1,
                0,
                0,
                1.0,
                1.0,
                0,
                0,
                0,
                0,
                List.of(),
                OutcomeRates.empty(),
                List.of(),
                now,
                now
        ));

        AgentRunEvaluateResponse response = new AgentRunEvaluate(
                runs, snapshots, suite, delivered, candidates, outcomes
        ).evaluate(RUN);

        ArgumentCaptor<EvalSuiteRequest> captor = ArgumentCaptor.forClass(EvalSuiteRequest.class);
        verify(snapshots).load(RUN);
        verify(suite).compare(captor.capture());
        EvalSuiteRequest request = captor.getValue();
        assertThat(request.suite()).isEqualTo(AgentRunEvaluate.SUITE);
        assertThat(request.kind()).isEqualTo(EvalSuiteKind.SHADOW_REPLAY);
        assertThat(request.replayMode()).isEqualTo(ReplayMode.FROZEN_REPLAY);
        assertThat(request.cases()).hasSize(1);
        assertThat(request.cases().getFirst().sourceRunId()).isEqualTo(RUN);
        assertThat(request.cases().getFirst().snapshot()).isSameAs(snapshot);
        assertThat(response.runId()).isEqualTo(RUN);
        assertThat(response.evalRunId()).isEqualTo(EVAL);
        assertThat(response.replayMode()).isEqualTo(ReplayMode.FROZEN_REPLAY);
        assertThat(response.versions()).containsExactly(AgentPolicyVersion.V1, AgentPolicyVersion.V2);
    }
}
