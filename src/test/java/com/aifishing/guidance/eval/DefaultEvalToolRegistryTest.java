package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.spi.AgentTool;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEvalToolRegistryTest {

    private final Clock clock = Clock.fixed(EvalFixtures.CLOCK, ZoneOffset.UTC);

    @Test
    void recordedObservationIsReplayedAndMissingToolsAreUnknown() {
        DefaultEvalToolRegistry registry = DefaultEvalToolRegistry.recorded(
                List.of(EvalFixtures.recordedNearby()), clock);

        AgentTool nearby = registry.get(ToolName.GET_NEARBY_WAYPOINTS).orElseThrow();
        assertThat(nearby.execute(request(ToolName.GET_NEARBY_WAYPOINTS)).status())
                .isEqualTo(ToolResultStatus.OK);
        assertThat(nearby.execute(request(ToolName.GET_NEARBY_WAYPOINTS)).data().get("count").asInt())
                .isEqualTo(2);

        AgentTool live = registry.get(ToolName.GET_LIVE_WAYPOINT_ACTIVITY).orElseThrow();
        assertThat(live.execute(request(ToolName.GET_LIVE_WAYPOINT_ACTIVITY)).status())
                .isEqualTo(ToolResultStatus.UNKNOWN);

        AgentTool historical = registry.get(ToolName.GET_HISTORICAL_PERFORMANCE).orElseThrow();
        assertThat(historical.execute(request(ToolName.GET_HISTORICAL_PERFORMANCE)).status())
                .isEqualTo(ToolResultStatus.UNKNOWN);
    }

    @Test
    void registryStaysEvalModeAndHasNoWriteApi() {
        DefaultEvalToolRegistry registry = DefaultEvalToolRegistry.unknownOnly(clock);
        assertThat(registry.executionMode().name()).isEqualTo("EVAL");
        assertThat(registry.all()).hasSize(ToolName.values().length);
    }

    private static ToolRequestEnvelope request(ToolName name) {
        return new ToolRequestEnvelope(
                com.aifishing.guidance.contracts.GuidanceSchemaVersion.VALUE,
                name,
                com.aifishing.guidance.contracts.GuidanceContracts.mapper().createObjectNode(),
                EvalFixtures.CLOCK
        );
    }
}
