package com.aifishing.guidance.obs;

import com.aifishing.guidance.contracts.AgentRunStatus;
import com.aifishing.guidance.contracts.DecisionValidationCheck;
import com.aifishing.guidance.contracts.GuidanceTrigger;
import com.aifishing.guidance.contracts.ToolName;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Stage timers as separate metric names. Allowed tags are only
 * {@code trigger}, {@code result}, {@code fallback}, {@code model},
 * {@code toolName}, {@code validationReason}. High-cardinality ids stay in MDC.
 */
@Component
public class GuidanceMetrics {

    public static final String ENVIRONMENT = "guidance.environment";
    public static final String STATE = "guidance.state";
    public static final String SAFETY = "guidance.safety";
    public static final String CONTEXT = "guidance.context";
    public static final String MODEL = "guidance.model";
    public static final String TOOLS = "guidance.tools";
    public static final String VALIDATION = "guidance.validation";
    public static final String PERSISTENCE = "guidance.persistence";
    public static final String TOTAL = "guidance.run";

    static final Set<String> ALLOWED_TAGS = Set.of(
            "trigger", "result", "fallback", "model", "toolName", "validationReason"
    );

    private final MeterRegistry registry;

    public GuidanceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T record(String metricName, Tags tags, Supplier<T> work) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return work.get();
        } finally {
            sample.stop(timer(metricName, tags));
        }
    }

    public void record(String metricName, Tags tags, Duration duration) {
        timer(metricName, tags).record(duration);
    }

    public void record(String metricName, Tags tags, Runnable work) {
        record(metricName, tags, () -> {
            work.run();
            return null;
        });
    }

    public static Tags base(GuidanceTrigger trigger, AgentRunStatus result, boolean fallback, String model) {
        return Tags.of(
                tag("trigger", trigger == null ? "unknown" : trigger.name()),
                tag("result", result == null ? "unknown" : result.name()),
                tag("fallback", Boolean.toString(fallback)),
                tag("model", sanitize(model))
        );
    }

    public static Tags withTool(Tags base, ToolName toolName) {
        return base.and(tag("toolName", toolName == null ? "unknown" : toolName.wire()));
    }

    public static Tags withValidation(Tags base, DecisionValidationCheck reason) {
        return base.and(tag("validationReason", reason == null ? "none" : reason.name()));
    }

    private Timer timer(String metricName, Tags tags) {
        return Timer.builder(metricName)
                .tags(assertAllowed(tags))
                .register(registry);
    }

    static Iterable<Tag> assertAllowed(Tags tags) {
        List<Tag> copy = new ArrayList<>();
        for (Tag tag : tags) {
            if (!ALLOWED_TAGS.contains(tag.getKey())) {
                throw new IllegalArgumentException(
                        "Micrometer tag '" + tag.getKey() + "' is not allowed on guidance metrics"
                );
            }
            copy.add(tag);
        }
        return copy;
    }

    private static Tag tag(String key, String value) {
        if (!ALLOWED_TAGS.contains(key)) {
            throw new IllegalArgumentException("Micrometer tag '" + key + "' is not allowed on guidance metrics");
        }
        return Tag.of(key, sanitize(value));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
