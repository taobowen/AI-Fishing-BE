package com.aifishing.guidance.versions;

import com.aifishing.guidance.spi.FishingAgentContextBuilder;

/**
 * Context builder used for a bound run, plus the stamped context version label.
 */
public record ContextProfile(
        String version,
        FishingAgentContextBuilder builder
) {
    public static final String GUIDANCE_CONTEXT_V1 = "guidance-context-v1";
}
