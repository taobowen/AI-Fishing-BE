package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.UsageTelemetry;
import com.aifishing.guidance.persistence.AgentRunEntity;
import com.aifishing.guidance.persistence.AgentRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists raw provider usage plus model identity. Never writes numeric 0 for
 * unknown token or cost fields.
 */
@Component
public class UsageTelemetryStore {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AgentRunRepository agentRunRepository;
    private final JdbcTemplate jdbcTemplate;

    public UsageTelemetryStore(AgentRunRepository agentRunRepository, JdbcTemplate jdbcTemplate) {
        this.agentRunRepository = agentRunRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void persistForAgentRun(
            UUID runId,
            Map<String, Object> rawProviderUsage,
            UsageTelemetry telemetry
    ) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(telemetry, "telemetry");
        AgentRunEntity entity = agentRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("agent run not found: " + runId));
        if (telemetry.modelProvider() != null) {
            entity.setModelProvider(telemetry.modelProvider());
        }
        if (telemetry.modelName() != null) {
            entity.setModelName(telemetry.modelName());
        }
        if (telemetry.modelVersion() != null) {
            entity.setModelVersion(telemetry.modelVersion());
        }
        if (telemetry.promptVersion() != null) {
            entity.setPromptVersion(telemetry.promptVersion());
        }
        entity.setRawProviderUsage(copyOrNull(rawProviderUsage));
        entity.setUsageTelemetry(asMap(telemetry));
        agentRunRepository.saveAndFlush(entity);
    }

    @Transactional
    public void persistForEvalCase(
            UUID evalCaseResultId,
            Map<String, Object> rawProviderUsage,
            UsageTelemetry telemetry
    ) {
        Objects.requireNonNull(evalCaseResultId, "evalCaseResultId");
        Objects.requireNonNull(telemetry, "telemetry");
        int updated = jdbcTemplate.update(
                """
                        update guidance_eval_case_results
                        set raw_provider_usage = cast(? as jsonb),
                            usage_telemetry = cast(? as jsonb)
                        where id = ?
                        """,
                json(copyOrNull(rawProviderUsage)),
                json(asMap(telemetry)),
                evalCaseResultId
        );
        if (updated == 0) {
            throw new IllegalStateException("eval case result not found: " + evalCaseResultId);
        }
    }

    private static Map<String, Object> copyOrNull(Map<String, Object> raw) {
        Map<String, Object> copy = UsageTelemetryAssembler.copyRaw(raw);
        return copy.isEmpty() ? null : copy;
    }

    private static Map<String, Object> asMap(UsageTelemetry telemetry) {
        return GuidanceContracts.mapper().convertValue(telemetry, MAP);
    }

    private static String json(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return GuidanceContracts.mapper().writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize usage telemetry", ex);
        }
    }
}
