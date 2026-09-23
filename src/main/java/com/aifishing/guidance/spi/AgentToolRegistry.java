package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.ToolName;

import java.util.Collection;
import java.util.Optional;

public interface AgentToolRegistry {

    Optional<AgentTool> get(ToolName name);

    Collection<AgentTool> all();
}
