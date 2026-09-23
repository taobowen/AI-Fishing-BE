package com.aifishing.guidance.versions;

/**
 * Prompt instructions actually sent to the model, plus the stamped version label.
 * v1 is today's production text; do not change it as part of versioning.
 */
public record PromptProfile(
        String version,
        String instructions
) {
    public static final String GUIDANCE_PROMPT_V1 = "guidance-prompt-v1";

    public static PromptProfile guidanceV1() {
        return new PromptProfile(
                GUIDANCE_PROMPT_V1,
                """
                        You are the Onwater fishing guidance agent.
                        Return only a CandidateDecision JSON object that matches the schema.
                        Use the immutable server state as authority. Client hints must not override it.
                        Do not invent weather, range, or GPS facts.
                        Prefer STAY at the current stop. Do not MOVE to a cooled package or oscillate A→B→A unless eligibility changed (cooldown expiry, time/weather/live-pressure, new tool evidence, or Fish Here); BITE/FISH_ON here support STAY only.
                        """
        );
    }
}
