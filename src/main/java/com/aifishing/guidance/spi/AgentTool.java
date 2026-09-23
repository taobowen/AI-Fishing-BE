package com.aifishing.guidance.spi;

import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;

public interface AgentTool {

    ToolName name();

    ToolResultEnvelope execute(ToolRequestEnvelope request);
}
