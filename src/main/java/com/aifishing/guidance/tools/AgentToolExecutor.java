package com.aifishing.guidance.tools;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.GuidanceSchemaVersion;
import com.aifishing.guidance.contracts.ToolCallRecord;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.contracts.ToolResultStatus;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.guidance.spi.AgentToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes one model-turn of independent tool calls. Same-turn calls may run in
 * parallel up to {@code app.guidance.maxConcurrentToolCalls}. D must call this
 * once per reasoning round and wait — this method does not start the next round.
 */
public class AgentToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolExecutor.class);
    private static final String SOURCE_RUNTIME = "tool-runtime";

    private final AgentToolRegistry registry;
    private final GuidanceProperties properties;
    private final Clock clock;

    public AgentToolExecutor(
            AgentToolRegistry registry,
            GuidanceProperties properties,
            Clock clock
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * API D should call after a {@code ModelTurnResult.ToolCalls} turn.
     *
     * <p>Enforces {@code maxToolRounds} and {@code maxToolCalls} independently.
     * Returns without invoking tools when either ceiling is already exhausted.
     */
    public ToolRoundResult executeRound(List<ToolRequestEnvelope> requests, ToolExecutionBudget budget) {
        Objects.requireNonNull(budget, "budget");
        if (requests == null || requests.isEmpty()) {
            return ToolRoundResult.executed(List.of());
        }
        if (!budget.hasRoundRemaining()) {
            return ToolRoundResult.skipped(ToolRoundResult.SkipReason.MAX_TOOL_ROUNDS);
        }
        if (!budget.hasCallRemaining()) {
            return ToolRoundResult.skipped(ToolRoundResult.SkipReason.MAX_TOOL_CALLS);
        }

        budget.consumeRound();
        int concurrency = Math.max(1, properties.getMaxConcurrentToolCalls());
        List<ScheduledCall> scheduled = new ArrayList<>();
        List<ToolCallRecord> records = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            records.add(null);
        }

        for (int i = 0; i < requests.size(); i++) {
            ToolRequestEnvelope request = requests.get(i);
            if (request == null || request.toolName() == null) {
                records.set(i, record(request, error(
                        ToolErrorType.INVALID_REQUEST,
                        false,
                        SOURCE_RUNTIME,
                        null
                ), 0));
                continue;
            }
            if (!budget.hasCallRemaining()) {
                records.set(i, record(request, error(
                        ToolErrorType.CALL_BUDGET_EXCEEDED,
                        false,
                        SOURCE_RUNTIME,
                        null
                ), 0));
                continue;
            }
            budget.consumeCall();
            scheduled.add(new ScheduledCall(i, request));
        }

        if (scheduled.isEmpty()) {
            return ToolRoundResult.executed(records);
        }

        Semaphore limiter = new Semaphore(concurrency);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(scheduled.size());
            for (ScheduledCall call : scheduled) {
                futures.add(CompletableFuture.runAsync(() -> {
                    limiter.acquireUninterruptibly();
                    try {
                        records.set(call.index, invoke(call.request, budget, pool));
                    } finally {
                        limiter.release();
                    }
                }, pool));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdownNow();
        }
        return ToolRoundResult.executed(records);
    }

    private ToolCallRecord invoke(ToolRequestEnvelope request, ToolExecutionBudget budget, ExecutorService pool) {
        long timeoutMs = budget.remainingToolTimeoutMs(clock);
        if (timeoutMs <= 0) {
            return record(request, error(ToolErrorType.TIMEOUT, true, SOURCE_RUNTIME, null), 0);
        }
        Optional<AgentTool> tool = registry.get(request.toolName());
        if (tool.isEmpty()) {
            return record(request, error(
                    ToolErrorType.TOOL_NOT_REGISTERED,
                    false,
                    sourceOf(request.toolName()),
                    null
            ), 0);
        }

        long started = System.nanoTime();
        CompletableFuture<ToolResultEnvelope> future = CompletableFuture.supplyAsync(
                () -> tool.get().execute(request),
                pool
        );
        try {
            ToolResultEnvelope raw = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return record(request, normalize(request.toolName(), raw), latencyMs(started));
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("Tool {} timed out after {}ms", request.toolName().wire(), timeoutMs);
            return record(request, error(ToolErrorType.TIMEOUT, true, sourceOf(request.toolName()), null), latencyMs(started));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("Tool {} interrupted", request.toolName().wire());
            return record(request, error(ToolErrorType.INTERNAL, true, sourceOf(request.toolName()), null), latencyMs(started));
        } catch (ExecutionException ex) {
            log.warn("Tool {} failed: {}", request.toolName().wire(), rootMessage(ex.getCause()));
            return record(request, error(ToolErrorType.INTERNAL, true, sourceOf(request.toolName()), null), latencyMs(started));
        } catch (RuntimeException ex) {
            future.cancel(true);
            log.warn("Tool {} failed: {}", request.toolName().wire(), rootMessage(ex));
            return record(request, error(ToolErrorType.INTERNAL, true, sourceOf(request.toolName()), null), latencyMs(started));
        }
    }

    private ToolResultEnvelope normalize(ToolName toolName, ToolResultEnvelope raw) {
        Instant observedAt = clock.instant();
        if (raw == null) {
            return error(ToolErrorType.INTERNAL, true, sourceOf(toolName), null);
        }
        String source = (raw.source() == null || raw.source().isBlank())
                ? sourceOf(toolName)
                : raw.source();
        ToolResultStatus status = raw.status() == null ? ToolResultStatus.ERROR : raw.status();
        JsonNode data = ToolResultSanitizer.stripForbiddenKeys(raw.data());
        if (data != null && !data.isObject() && !data.isNull()) {
            return new ToolResultEnvelope(
                    GuidanceSchemaVersion.VALUE,
                    ToolResultStatus.INVALID_DATA,
                    observedAt,
                    source,
                    null,
                    null,
                    null,
                    "data"
            );
        }
        int maxBytes = Math.max(0, properties.getMaxToolResultBytes());
        if (data != null && ToolResultSanitizer.utf8Bytes(data) > maxBytes) {
            data = ToolResultSanitizer.truncateToBudget(data, maxBytes);
            if (data == null) {
                return error(ToolErrorType.RESULT_TOO_LARGE, false, source, null);
            }
        }
        status = ToolResultSanitizer.normalizeEmptyOk(status, data);
        if (status == ToolResultStatus.UNKNOWN && ToolResultSanitizer.isEmptyData(data)) {
            data = null;
        }
        String errorType = raw.errorType();
        Boolean retryable = raw.retryable();
        if (status == ToolResultStatus.ERROR) {
            errorType = ToolResultSanitizer.sanitizeErrorType(errorType);
            if (retryable == null) {
                retryable = Boolean.TRUE;
            }
        } else {
            errorType = null;
            retryable = null;
        }
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                status,
                raw.observedAt() == null ? observedAt : raw.observedAt(),
                source,
                data,
                errorType,
                retryable,
                status == ToolResultStatus.INVALID_DATA ? raw.field() : null
        );
    }

    private ToolResultEnvelope error(String errorType, boolean retryable, String source, String field) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.ERROR,
                clock.instant(),
                source,
                null,
                errorType,
                retryable,
                field
        );
    }

    private ToolCallRecord record(ToolRequestEnvelope request, ToolResultEnvelope result, int latencyMs) {
        Instant observedAt = result.observedAt() != null ? result.observedAt() : clock.instant();
        ToolRequestEnvelope safeRequest = request != null
                ? request
                : new ToolRequestEnvelope(
                        GuidanceSchemaVersion.VALUE,
                        ToolName.GET_NEARBY_WAYPOINTS,
                        GuidanceContracts.mapper().createObjectNode(),
                        observedAt
                );
        return new ToolCallRecord(
                GuidanceSchemaVersion.VALUE,
                safeRequest,
                result,
                Math.max(0, latencyMs),
                observedAt
        );
    }

    private static String sourceOf(ToolName name) {
        return name == null ? SOURCE_RUNTIME : name.wire();
    }

    private static int latencyMs(long startedNanos) {
        return (int) Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static String rootMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "unavailable";
        }
        String message = error.getMessage();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private record ScheduledCall(int index, ToolRequestEnvelope request) {
    }
}
