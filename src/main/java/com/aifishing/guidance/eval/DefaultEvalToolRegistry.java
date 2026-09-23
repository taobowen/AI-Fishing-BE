package com.aifishing.guidance.eval;

import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.guidance.spi.EvalToolRegistry;

import java.time.Clock;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only EVAL registry. Recorded observations are replayed as copies.
 * Missing or write-path tools return {@code UNKNOWN}. Live weather/memory
 * stores are never consulted.
 */
public final class DefaultEvalToolRegistry implements EvalToolRegistry {

    private final Map<ToolName, AgentTool> tools;

    public static DefaultEvalToolRegistry recorded(List<ToolCallRecord> observations, Clock clock) {
        EnumMap<ToolName, AgentTool> indexed = new EnumMap<>(ToolName.class);
        Clock frozen = clock == null ? Clock.systemUTC() : clock;
        if (observations != null) {
            for (ToolCallRecord record : observations) {
                if (record == null || record.request() == null || record.request().toolName() == null) {
                    continue;
                }
                indexed.putIfAbsent(record.request().toolName(), new RecordedTool(record, frozen));
            }
        }
        for (ToolName name : ToolName.values()) {
            indexed.putIfAbsent(name, new UnknownTool(name, frozen));
        }
        return new DefaultEvalToolRegistry(indexed);
    }

    public static DefaultEvalToolRegistry unknownOnly(Clock clock) {
        return recorded(List.of(), clock);
    }

    private DefaultEvalToolRegistry(Map<ToolName, AgentTool> tools) {
        this.tools = Map.copyOf(tools);
    }

    @Override
    public Optional<AgentTool> get(ToolName name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public Collection<AgentTool> all() {
        return tools.values();
    }

    private static final class RecordedTool implements AgentTool {
        private final ToolCallRecord recorded;
        private final Clock clock;

        private RecordedTool(ToolCallRecord recorded, Clock clock) {
            this.recorded = recorded;
            this.clock = clock;
        }

        @Override
        public ToolName name() {
            return recorded.request().toolName();
        }

        @Override
        public ToolResultEnvelope execute(ToolRequestEnvelope request) {
            ToolResultEnvelope result = recorded.result();
            if (result == null) {
                return unknown(name(), clock);
            }
            return new ToolResultEnvelope(
                    GuidanceSchemaVersion.VALUE,
                    result.status(),
                    result.observedAt() == null ? clock.instant() : result.observedAt(),
                    result.source() == null ? name().wire() : result.source(),
                    result.data(),
                    result.errorType(),
                    result.retryable(),
                    result.field()
            );
        }
    }

    private static final class UnknownTool implements AgentTool {
        private final ToolName name;
        private final Clock clock;

        private UnknownTool(ToolName name, Clock clock) {
            this.name = name;
            this.clock = clock;
        }

        @Override
        public ToolName name() {
            return name;
        }

        @Override
        public ToolResultEnvelope execute(ToolRequestEnvelope request) {
            return unknown(name, clock);
        }
    }

    static ToolResultEnvelope unknown(ToolName name, Clock clock) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.UNKNOWN,
                clock.instant(),
                name.wire(),
                null,
                null,
                null,
                null
        );
    }
}
