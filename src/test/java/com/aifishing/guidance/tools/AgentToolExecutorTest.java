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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-16T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void emptyRoundDoesNotConsumeBudget() {
        AgentToolExecutor executor = executor(InMemoryAgentToolRegistry.empty(), properties(4, 12, 4, 65_536));
        ToolExecutionBudget budget = budget(4, 12, 1_000);

        ToolRoundResult result = executor.executeRound(List.of(), budget);

        assertThat(result.executed()).isTrue();
        assertThat(result.records()).isEmpty();
        assertThat(budget.roundsUsed()).isZero();
        assertThat(budget.callsUsed()).isZero();
    }

    @Test
    void toolTimeoutIsCappedByRemainingRunDeadline() {
        ToolExecutionBudget budget = ToolExecutionBudget.from(properties(4, 12, 4, 65_536))
                .toolCallTimeoutMs(5_000)
                .runDeadline(NOW.plusMillis(25))
                .build();

        assertThat(budget.remainingToolTimeoutMs(CLOCK)).isEqualTo(25L);
        assertThat(budget.maxRounds()).isEqualTo(4);
        assertThat(budget.maxCalls()).isEqualTo(12);
    }

    @Test
    void emptyFactoryHasNoTools() {
        InMemoryAgentToolRegistry registry = InMemoryAgentToolRegistry.empty();
        assertThat(registry.all()).isEmpty();
        for (ToolName name : ToolName.values()) {
            assertThat(registry.get(name)).isEmpty();
        }
    }

    @Test
    void emptyRegistryReturnsErrorWithoutInvokingGis() {
        AgentToolExecutor executor = executor(InMemoryAgentToolRegistry.empty(), properties(4, 12, 4, 65_536));
        ToolRoundResult result = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS)),
                budget(4, 12, 1_000)
        );

        assertThat(result.executed()).isTrue();
        assertThat(result.records()).hasSize(1);
        ToolResultEnvelope envelope = result.records().getFirst().result();
        assertThat(envelope.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(envelope.errorType()).isEqualTo(ToolErrorType.TOOL_NOT_REGISTERED);
        assertThat(envelope.retryable()).isFalse();
        assertNoStackTrace(envelope);
        assertValidRecord(result.records().getFirst());
    }

    @Test
    void sameTurnIndependentToolsRunInParallel() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(
                        latching(ToolName.GET_NEARBY_WAYPOINTS, barrier),
                        latching(ToolName.GET_WAYPOINT_STRUCTURE, barrier),
                        latching(ToolName.GET_HISTORICAL_PERFORMANCE, barrier)
                ),
                properties(4, 12, 3, 65_536)
        );

        long started = System.nanoTime();
        ToolRoundResult result = executor.executeRound(
                List.of(
                        request(ToolName.GET_NEARBY_WAYPOINTS),
                        request(ToolName.GET_WAYPOINT_STRUCTURE),
                        request(ToolName.GET_HISTORICAL_PERFORMANCE)
                ),
                budget(4, 12, 2_000)
        );
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertThat(result.executed()).isTrue();
        assertThat(result.records()).hasSize(3);
        assertThat(result.records()).allMatch(record -> record.result().status() == ToolResultStatus.OK);
        assertThat(elapsedMs).isLessThan(1_500L);
        assertThat(barrier.getNumberWaiting()).isZero();
    }

    @Test
    void sameTurnHonorsMaxConcurrentToolCalls() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        ToolHandler gated = request -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(40);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            return ok(request.toolName(), data("ok", true));
        };
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(
                        named(ToolName.GET_NEARBY_WAYPOINTS, gated),
                        named(ToolName.GET_WAYPOINT_STRUCTURE, gated),
                        named(ToolName.GET_LIVE_WAYPOINT_ACTIVITY, gated)
                ),
                properties(4, 12, 1, 65_536)
        );

        executor.executeRound(
                List.of(
                        request(ToolName.GET_NEARBY_WAYPOINTS),
                        request(ToolName.GET_WAYPOINT_STRUCTURE),
                        request(ToolName.GET_LIVE_WAYPOINT_ACTIVITY)
                ),
                budget(4, 12, 2_000)
        );

        assertThat(maxInFlight.get()).isEqualTo(1);
    }

    @Test
    void laterRoundDoesNotStartUntilEarlierRoundFinishes() {
        AtomicLong round1End = new AtomicLong();
        AtomicLong round2Start = new AtomicLong();
        AgentTool first = named(ToolName.GET_NEARBY_WAYPOINTS, request -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            round1End.set(System.nanoTime());
            return ok(ToolName.GET_NEARBY_WAYPOINTS, data("round", 1));
        });
        AgentTool second = named(ToolName.GET_WAYPOINT_STRUCTURE, request -> {
            round2Start.set(System.nanoTime());
            return ok(ToolName.GET_WAYPOINT_STRUCTURE, data("round", 2));
        });
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(first, second),
                properties(4, 12, 4, 65_536)
        );
        ToolExecutionBudget budget = budget(4, 12, 2_000);

        ToolRoundResult firstRound = executor.executeRound(List.of(request(ToolName.GET_NEARBY_WAYPOINTS)), budget);
        ToolRoundResult secondRound = executor.executeRound(List.of(request(ToolName.GET_WAYPOINT_STRUCTURE)), budget);

        assertThat(firstRound.executed()).isTrue();
        assertThat(secondRound.executed()).isTrue();
        assertThat(round2Start.get()).isGreaterThanOrEqualTo(round1End.get());
        assertThat(budget.roundsUsed()).isEqualTo(2);
    }

    @Test
    void maxToolRoundsIsIndependentOfRemainingCalls() {
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(okTool(ToolName.GET_NEARBY_WAYPOINTS)),
                properties(1, 10, 4, 65_536)
        );
        ToolExecutionBudget budget = budget(1, 10, 1_000);

        ToolRoundResult first = executor.executeRound(List.of(request(ToolName.GET_NEARBY_WAYPOINTS)), budget);
        ToolRoundResult second = executor.executeRound(List.of(request(ToolName.GET_NEARBY_WAYPOINTS)), budget);

        assertThat(first.executed()).isTrue();
        assertThat(first.records()).hasSize(1);
        assertThat(second.executed()).isFalse();
        assertThat(second.skipReason()).isEqualTo(ToolRoundResult.SkipReason.MAX_TOOL_ROUNDS);
        assertThat(second.records()).isEmpty();
        assertThat(budget.roundsUsed()).isEqualTo(1);
        assertThat(budget.callsUsed()).isEqualTo(1);
        assertThat(budget.remainingCalls()).isEqualTo(9);
    }

    @Test
    void maxToolCallsIsIndependentOfRemainingRounds() {
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(
                        okTool(ToolName.GET_NEARBY_WAYPOINTS),
                        okTool(ToolName.GET_WAYPOINT_STRUCTURE)
                ),
                properties(10, 1, 4, 65_536)
        );
        ToolExecutionBudget budget = budget(10, 1, 1_000);

        ToolRoundResult first = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS), request(ToolName.GET_WAYPOINT_STRUCTURE)),
                budget
        );
        ToolRoundResult second = executor.executeRound(List.of(request(ToolName.GET_NEARBY_WAYPOINTS)), budget);

        assertThat(first.executed()).isTrue();
        assertThat(first.records()).hasSize(2);
        assertThat(first.records().getFirst().result().status()).isEqualTo(ToolResultStatus.OK);
        assertThat(first.records().get(1).result().status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(first.records().get(1).result().errorType()).isEqualTo(ToolErrorType.CALL_BUDGET_EXCEEDED);
        assertThat(first.records().get(1).result().retryable()).isFalse();
        assertThat(second.executed()).isFalse();
        assertThat(second.skipReason()).isEqualTo(ToolRoundResult.SkipReason.MAX_TOOL_CALLS);
        assertThat(budget.roundsUsed()).isEqualTo(1);
        assertThat(budget.callsUsed()).isEqualTo(1);
        assertThat(budget.remainingRounds()).isEqualTo(9);
    }

    @Test
    void perToolTimeoutIsRetryableErrorWithoutStackTrace() {
        AgentTool slow = named(ToolName.GET_NEARBY_WAYPOINTS, request -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ok(ToolName.GET_NEARBY_WAYPOINTS, data("late", true));
        });
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(slow),
                properties(4, 12, 4, 65_536)
        );

        long started = System.nanoTime();
        ToolRoundResult result = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS)),
                budget(4, 12, 40)
        );
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

        assertThat(result.records()).hasSize(1);
        ToolResultEnvelope envelope = result.records().getFirst().result();
        assertThat(envelope.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(envelope.errorType()).isEqualTo(ToolErrorType.TIMEOUT);
        assertThat(envelope.retryable()).isTrue();
        assertNoStackTrace(envelope);
        assertThat(elapsedMs).isLessThan(300L);
        assertValidRecord(result.records().getFirst());
    }

    @Test
    void emptyDataBecomesUnknown() {
        AgentTool empty = named(ToolName.GET_NEARBY_WAYPOINTS, request -> ok(
                ToolName.GET_NEARBY_WAYPOINTS,
                GuidanceContracts.mapper().createObjectNode()
        ));
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(empty),
                properties(4, 12, 4, 65_536)
        );

        ToolResultEnvelope envelope = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS)),
                budget(4, 12, 1_000)
        ).records().getFirst().result();

        assertThat(envelope.status()).isEqualTo(ToolResultStatus.UNKNOWN);
        assertThat(envelope.data()).isNull();
        assertThat(envelope.errorType()).isNull();
        assertValidEnvelope(envelope);
    }

    @Test
    void infraFailureIsRetryableErrorWithoutStackTrace() {
        AgentTool exploding = named(ToolName.GET_NEARBY_WAYPOINTS, request -> {
            throw new IllegalStateException("db down\n\tat com.example.GisClient.connect(GisClient.java:12)");
        });
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(exploding),
                properties(4, 12, 4, 65_536)
        );

        ToolResultEnvelope envelope = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS)),
                budget(4, 12, 1_000)
        ).records().getFirst().result();

        assertThat(envelope.status()).isEqualTo(ToolResultStatus.ERROR);
        assertThat(envelope.errorType()).isEqualTo(ToolErrorType.INTERNAL);
        assertThat(envelope.retryable()).isTrue();
        assertNoStackTrace(envelope);
        assertValidEnvelope(envelope);
    }

    @Test
    void oversizedResultIsTruncatedAndStripsForbiddenKeys() {
        ObjectNode huge = GuidanceContracts.mapper().createObjectNode();
        huge.put("stackTrace", "java.lang.RuntimeException\n\tat com.example.Tool.execute");
        huge.put("payload", "x".repeat(8_000));
        huge.put("keep", "yes");
        AgentTool noisy = named(ToolName.GET_NEARBY_WAYPOINTS, request -> ok(ToolName.GET_NEARBY_WAYPOINTS, huge));
        AgentToolExecutor executor = executor(
                new InMemoryAgentToolRegistry(noisy),
                properties(4, 12, 4, 64)
        );

        ToolCallRecord record = executor.executeRound(
                List.of(request(ToolName.GET_NEARBY_WAYPOINTS)),
                budget(4, 12, 1_000)
        ).records().getFirst();
        ToolResultEnvelope envelope = record.result();

        assertThat(envelope.status()).isIn(ToolResultStatus.OK, ToolResultStatus.UNKNOWN);
        if (envelope.data() != null) {
            assertThat(envelope.data().has("stackTrace")).isFalse();
            assertThat(envelope.data().has("stack_trace")).isFalse();
            assertThat(ToolResultSanitizer.utf8Bytes(envelope.data())).isLessThanOrEqualTo(64);
            assertThat(envelope.data().path("payload").asText("")).doesNotContain("x".repeat(100));
        }
        assertNoStackTrace(envelope);
        assertValidRecord(record);
    }

    private static AgentToolExecutor executor(InMemoryAgentToolRegistry registry, GuidanceProperties properties) {
        return new AgentToolExecutor(registry, properties, CLOCK);
    }

    private static GuidanceProperties properties(int rounds, int calls, int concurrent, int maxBytes) {
        GuidanceProperties properties = new GuidanceProperties();
        properties.setRuntimeMode(GuidanceProperties.RuntimeMode.DETERMINISTIC);
        properties.setMaxToolRounds(rounds);
        properties.setMaxToolCalls(calls);
        properties.setMaxConcurrentToolCalls(concurrent);
        properties.setMaxToolResultBytes(maxBytes);
        return properties;
    }

    private static ToolExecutionBudget budget(int rounds, int calls, long timeoutMs) {
        return ToolExecutionBudget.builder()
                .maxRounds(rounds)
                .maxCalls(calls)
                .toolCallTimeoutMs(timeoutMs)
                .build();
    }

    private static ToolRequestEnvelope request(ToolName name) {
        ObjectNode args = GuidanceContracts.mapper().createObjectNode();
        args.put("query", "stub");
        return new ToolRequestEnvelope(GuidanceSchemaVersion.VALUE, name, args, NOW);
    }

    private static AgentTool okTool(ToolName name) {
        return named(name, request -> ok(name, data("ok", true)));
    }

    private static AgentTool latching(ToolName name, CyclicBarrier barrier) {
        return named(name, request -> {
            try {
                barrier.await(2, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("tools did not run in the same turn", ex);
            }
            return ok(name, data("ok", true));
        });
    }

    @FunctionalInterface
    private interface ToolHandler {
        ToolResultEnvelope execute(ToolRequestEnvelope request);
    }

    private static AgentTool named(ToolName name, ToolHandler body) {
        return new AgentTool() {
            @Override
            public ToolName name() {
                return name;
            }

            @Override
            public ToolResultEnvelope execute(ToolRequestEnvelope request) {
                return body.execute(request);
            }
        };
    }

    private static ToolResultEnvelope ok(ToolName name, JsonNode data) {
        return new ToolResultEnvelope(
                GuidanceSchemaVersion.VALUE,
                ToolResultStatus.OK,
                NOW,
                name.wire(),
                data,
                null,
                null,
                null
        );
    }

    private static ObjectNode data(String key, Object value) {
        ObjectNode node = GuidanceContracts.mapper().createObjectNode();
        if (value instanceof Boolean flag) {
            node.put(key, flag);
        } else if (value instanceof Integer number) {
            node.put(key, number);
        } else {
            node.put(key, String.valueOf(value));
        }
        return node;
    }

    private static void assertNoStackTrace(ToolResultEnvelope envelope) {
        String blob = String.valueOf(envelope);
        assertThat(blob).doesNotContain("at com.").doesNotContain("stackTrace").doesNotContain("stack_trace");
        if (envelope.data() != null) {
            assertThat(envelope.data().has("stackTrace")).isFalse();
            assertThat(envelope.data().has("stack_trace")).isFalse();
        }
        if (envelope.errorType() != null) {
            assertThat(envelope.errorType()).doesNotContain("\n").doesNotContain("Exception");
        }
    }

    private static void assertValidRecord(ToolCallRecord record) {
        Set<ValidationMessage> errors = GuidanceContracts.schema("ToolCallRecord").validate(json(record));
        assertThat(errors).isEmpty();
    }

    private static void assertValidEnvelope(ToolResultEnvelope envelope) {
        Set<ValidationMessage> errors = GuidanceContracts.schema("ToolResultEnvelope").validate(json(envelope));
        assertThat(errors).isEmpty();
    }

    private static JsonNode json(Object value) {
        return GuidanceContracts.mapper().valueToTree(value);
    }
}
