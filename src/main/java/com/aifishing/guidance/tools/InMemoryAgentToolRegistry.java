package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.guidance.spi.AgentToolRegistry;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry. Production wires the six real tools through
 * {@link GuidanceToolsConfiguration}. {@link #empty()} remains a test helper.
 */
public final class InMemoryAgentToolRegistry implements AgentToolRegistry {

    private final Map<ToolName, AgentTool> tools;

    public static InMemoryAgentToolRegistry empty() {
        return new InMemoryAgentToolRegistry(List.of());
    }

    public InMemoryAgentToolRegistry(AgentTool... tools) {
        this(List.of(tools));
    }

    public InMemoryAgentToolRegistry(Collection<AgentTool> tools) {
        EnumMap<ToolName, AgentTool> indexed = new EnumMap<>(ToolName.class);
        if (tools != null) {
            for (AgentTool tool : tools) {
                if (tool == null || tool.name() == null) {
                    throw new IllegalArgumentException("AgentTool and name are required");
                }
                AgentTool previous = indexed.put(tool.name(), tool);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate tool registration: " + tool.name());
                }
            }
        }
        this.tools = Map.copyOf(indexed);
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
}
