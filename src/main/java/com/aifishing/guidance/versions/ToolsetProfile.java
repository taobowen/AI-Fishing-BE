package com.aifishing.guidance.versions;

import com.aifishing.guidance.spi.AgentToolRegistry;

/**
 * Tool registry used for a bound run, plus the stamped tool-schema version label.
 */
public record ToolsetProfile(
        String version,
        AgentToolRegistry registry
) {
    public static final String GUIDANCE_TOOLS_V1 = "guidance-tools-v1";
}
