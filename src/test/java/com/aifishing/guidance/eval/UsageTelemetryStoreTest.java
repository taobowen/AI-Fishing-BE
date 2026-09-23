package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.UsageTelemetry;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageTelemetryStoreTest {

    @Test
    void persistForAgentRunKeepsModelIdentityAndOmitsUnknownTokens() {
        AgentRunRepository repository = mock(AgentRunRepository.class);
        AgentRunEntity entity = new AgentRunEntity();
        UUID runId = UUID.randomUUID();
        when(repository.findById(runId)).thenReturn(Optional.of(entity));

        UsageTelemetry telemetry = UsageTelemetryAssembler.assemble(
                "deterministic", "fixture", "1", "prompt-1",
                Map.of(),
                TokenPricing.none()
        );
        new UsageTelemetryStore(repository, mock(JdbcTemplate.class))
                .persistForAgentRun(runId, Map.of("id", "resp-1"), telemetry);

        assertThat(entity.getModelProvider()).isEqualTo("deterministic");
        assertThat(entity.getModelName()).isEqualTo("fixture");
        assertThat(entity.getModelVersion()).isEqualTo("1");
        assertThat(entity.getPromptVersion()).isEqualTo("prompt-1");
        assertThat(entity.getRawProviderUsage()).containsEntry("id", "resp-1");
        assertThat(entity.getUsageTelemetry())
                .containsEntry("modelProvider", "deterministic")
                .doesNotContainKeys("inputTokens", "outputTokens", "totalTokens", "costUsd");
        verify(repository).saveAndFlush(entity);
    }
}
