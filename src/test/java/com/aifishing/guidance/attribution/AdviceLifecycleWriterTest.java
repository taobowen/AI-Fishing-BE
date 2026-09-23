package com.aifishing.guidance.attribution;

import com.aifishing.guidance.GuidancePhase2Fixtures;
import com.aifishing.guidance.contracts.AgentRunResult;
import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.guidance.persistence.AgentDeliveredDecisionRepository;
import com.aifishing.guidance.runtime.KillSwitchContinuation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.aifishing.guidance.GuidancePhase2Fixtures.SESSION_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdviceLifecycleWriterTest {

    @Mock
    private AgentDeliveredDecisionRepository deliveredRepository;
    @Mock
    private SessionEventWriter sessionEventWriter;
    @Mock
    private GuidanceLearningHooks learningHooks;

    @Test
    void killSwitchDoesNotWriteAdviceCreatedOrAttribution() {
        AdviceLifecycleWriter writer = new AdviceLifecycleWriter(
                deliveredRepository,
                sessionEventWriter,
                learningHooks,
                Clock.fixed(Instant.parse("2026-09-18T14:00:00Z"), ZoneOffset.UTC)
        );
        AgentRunResult result = new AgentRunResult(
                GuidanceSchemaVersion.VALUE,
                GuidancePhase2Fixtures.DECISION_ID,
                AgentRunStatus.FALLBACK,
                null,
                List.of(),
                null,
                null,
                KillSwitchContinuation.from(GuidancePhase2Fixtures.DECISION_ID, GuidancePhase2Fixtures.safeState())
        );

        writer.writeCreated(SESSION_ID, result);

        verify(sessionEventWriter, never()).writeAudit(any(), any());
        verify(learningHooks, never()).onAdviceDelivered(any(), any(), any());
        verify(deliveredRepository, never()).findFirstByRunId(any());
    }
}
