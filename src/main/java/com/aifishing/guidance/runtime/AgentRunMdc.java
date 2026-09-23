package com.aifishing.guidance.runtime;

import com.aifishing.guidance.contracts.GuidanceTrigger;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * High-cardinality ids belong in MDC only — never as Micrometer tags.
 */
public final class AgentRunMdc {

    public static final String TRACE_ID = "traceId";
    public static final String RUN_ID = "runId";
    public static final String SESSION_ID = "sessionId";
    public static final String TRIGGER = "trigger";

    private AgentRunMdc() {
    }

    public static UUID sessionId() {
        String value = MDC.get(SESSION_ID);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String resolveTraceId() {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }

    public static Scope open(UUID runId, UUID sessionId, GuidanceTrigger trigger, String traceId) {
        String previousTrace = MDC.get(TRACE_ID);
        String previousRun = MDC.get(RUN_ID);
        String previousSession = MDC.get(SESSION_ID);
        String previousTrigger = MDC.get(TRIGGER);
        MDC.put(TRACE_ID, traceId);
        MDC.put(RUN_ID, runId.toString());
        MDC.put(SESSION_ID, sessionId.toString());
        MDC.put(TRIGGER, trigger.name());
        if (MDC.get("requestId") == null && traceId != null) {
            MDC.put("requestId", traceId);
        }
        return new Scope(previousTrace, previousRun, previousSession, previousTrigger);
    }

    public static final class Scope implements AutoCloseable {
        private final String previousTrace;
        private final String previousRun;
        private final String previousSession;
        private final String previousTrigger;

        private Scope(String previousTrace, String previousRun, String previousSession, String previousTrigger) {
            this.previousTrace = previousTrace;
            this.previousRun = previousRun;
            this.previousSession = previousSession;
            this.previousTrigger = previousTrigger;
        }

        @Override
        public void close() {
            restore(TRACE_ID, previousTrace);
            restore(RUN_ID, previousRun);
            restore(SESSION_ID, previousSession);
            restore(TRIGGER, previousTrigger);
        }

        private static void restore(String key, String previous) {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
