package com.aifishing.guidance.tools;

import com.aifishing.guidance.GuidanceProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-run tool budget. {@code maxToolRounds} and {@code maxToolCalls} are independent
 * ceilings — exhausting one does not raise the other. D owns the model loop and
 * reuses one instance across serial reasoning rounds.
 */
public final class ToolExecutionBudget {

    public static final long DEFAULT_TOOL_CALL_TIMEOUT_MS = 5_000L;

    private final int maxRounds;
    private final int maxCalls;
    private final long toolCallTimeoutMs;
    private final Instant runDeadline;
    private int roundsUsed;
    private int callsUsed;

    private ToolExecutionBudget(
            int maxRounds,
            int maxCalls,
            long toolCallTimeoutMs,
            Instant runDeadline
    ) {
        this.maxRounds = Math.max(0, maxRounds);
        this.maxCalls = Math.max(0, maxCalls);
        this.toolCallTimeoutMs = Math.max(0, toolCallTimeoutMs);
        this.runDeadline = runDeadline;
    }

    public static Builder from(GuidanceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return new Builder()
                .maxRounds(properties.getMaxToolRounds())
                .maxCalls(properties.getMaxToolCalls());
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasRoundRemaining() {
        return roundsUsed < maxRounds;
    }

    public boolean hasCallRemaining() {
        return callsUsed < maxCalls;
    }

    public int remainingRounds() {
        return Math.max(0, maxRounds - roundsUsed);
    }

    public int remainingCalls() {
        return Math.max(0, maxCalls - callsUsed);
    }

    public int maxRounds() {
        return maxRounds;
    }

    public int maxCalls() {
        return maxCalls;
    }

    public int roundsUsed() {
        return roundsUsed;
    }

    public int callsUsed() {
        return callsUsed;
    }

    public long toolCallTimeoutMs() {
        return toolCallTimeoutMs;
    }

    public Instant runDeadline() {
        return runDeadline;
    }

    /**
     * Per-tool timeout for the next invocation: configured tool timeout, capped
     * by remaining run wall-clock when a deadline is set. Never uses
     * {@code modelCallTimeoutMs}.
     */
    public long remainingToolTimeoutMs(Clock clock) {
        long remainingRun = remainingRunMs(clock);
        return Math.min(toolCallTimeoutMs, remainingRun);
    }

    public long remainingRunMs(Clock clock) {
        if (runDeadline == null) {
            return Long.MAX_VALUE;
        }
        Instant now = Objects.requireNonNull(clock, "clock").instant();
        long remaining = runDeadline.toEpochMilli() - now.toEpochMilli();
        return Math.max(0L, remaining);
    }

    void consumeRound() {
        if (!hasRoundRemaining()) {
            throw new IllegalStateException("maxToolRounds exhausted");
        }
        roundsUsed++;
    }

    void consumeCall() {
        if (!hasCallRemaining()) {
            throw new IllegalStateException("maxToolCalls exhausted");
        }
        callsUsed++;
    }

    public static final class Builder {

        private int maxRounds = 4;
        private int maxCalls = 12;
        private long toolCallTimeoutMs = DEFAULT_TOOL_CALL_TIMEOUT_MS;
        private Instant runDeadline;

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public Builder maxCalls(int maxCalls) {
            this.maxCalls = maxCalls;
            return this;
        }

        public Builder toolCallTimeoutMs(long toolCallTimeoutMs) {
            this.toolCallTimeoutMs = toolCallTimeoutMs;
            return this;
        }

        public Builder runDeadline(Instant runDeadline) {
            this.runDeadline = runDeadline;
            return this;
        }

        public ToolExecutionBudget build() {
            return new ToolExecutionBudget(maxRounds, maxCalls, toolCallTimeoutMs, runDeadline);
        }
    }
}
