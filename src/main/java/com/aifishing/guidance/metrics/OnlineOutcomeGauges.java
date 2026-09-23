package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.AttributionDimension;
import com.aifishing.guidance.contracts.GuidanceSuccessKind;
import com.aifishing.guidance.contracts.OnlineGuidanceMetrics;
import com.aifishing.guidance.contracts.OnlineMetricGrain;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Low-cardinality online outcome gauges. Session and user ids stay out of tags.
 */
@Component
public class OnlineOutcomeGauges {

    public static final String SUCCESS_COUNT = "guidance.online.success.count";
    public static final String FOLLOW_THROUGH_RATE = "guidance.online.follow_through.rate";
    public static final String ACCEPTANCE_RATE = "guidance.online.acceptance.rate";
    public static final String REJECT_RATE = "guidance.online.reject.rate";
    public static final String OVERRIDE_RATE = "guidance.online.override.rate";
    public static final String EFFORT_SECONDS = "guidance.online.effort.seconds";
    public static final String FISH_ON_PER_HOUR = "guidance.online.fish_on_per_hour";
    public static final String BITE_PER_HOUR = "guidance.online.bite_per_hour";
    public static final String LANDING_COUNT = "guidance.online.landing.count";

    static final Set<String> ALLOWED_TAGS = Set.of("dimension", "grain", "kind", "result");

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicReference<Double>> values = new ConcurrentHashMap<>();

    public OnlineOutcomeGauges(MeterRegistry registry) {
        this.registry = registry;
    }

    public void publish(OnlineMetricGrain grain, OnlineOutcomeSnapshot snapshot) {
        if (snapshot == null || snapshot.metrics() == null) {
            return;
        }
        OnlineGuidanceMetrics metrics = snapshot.metrics();
        Tags base = Tags.of(
                tag("dimension", dimensionValue(metrics.attributionDimension())),
                tag("grain", grain == null ? "unknown" : grain.name())
        );
        set(SUCCESS_COUNT, base.and(tag("kind", GuidanceSuccessKind.FISH_ON_SUCCESS.name())), metrics.fishOnSuccessCount());
        set(SUCCESS_COUNT, base.and(tag("kind", GuidanceSuccessKind.BITE_SIGNAL_ONLY.name())), metrics.biteSignalOnlyCount());
        set(SUCCESS_COUNT, base.and(tag("kind", GuidanceSuccessKind.NO_FISH_SIGNAL.name())), metrics.noFishSignalCount());
        set(SUCCESS_COUNT, base.and(tag("kind", GuidanceSuccessKind.NOT_FOLLOWED.name())), metrics.notFollowedCount());
        set(SUCCESS_COUNT, base.and(tag("kind", GuidanceSuccessKind.UNATTRIBUTED.name())), metrics.unattributedCount());
        setRate(FOLLOW_THROUGH_RATE, base, metrics.observedFollowThroughRate());
        setRate(ACCEPTANCE_RATE, base, metrics.explicitAcceptanceRate());
        setRate(REJECT_RATE, base, metrics.rejectRate());
        setRate(OVERRIDE_RATE, base, metrics.overrideRate());
        set(EFFORT_SECONDS, base, metrics.effectiveFishingEffortSeconds());
        setRate(FISH_ON_PER_HOUR, base, metrics.fishOnPerFishingHourAfterRecommendation());
        setRate(BITE_PER_HOUR, base, metrics.bitePerFishingHourAfterRecommendation());
        LandingAnalytics landing = snapshot.landing();
        set(LANDING_COUNT, base.and(tag("result", "landed")), landing == null ? 0 : landing.landedCount());
        set(LANDING_COUNT, base.and(tag("result", "lost")), landing == null ? 0 : landing.lostCount());
    }

    static Iterable<Tag> assertAllowed(Tags tags) {
        List<Tag> copy = new ArrayList<>();
        for (Tag tag : tags) {
            if (!ALLOWED_TAGS.contains(tag.getKey())) {
                throw new IllegalArgumentException(
                        "Micrometer tag '" + tag.getKey() + "' is not allowed on online outcome metrics"
                );
            }
            copy.add(tag);
        }
        return copy;
    }

    private void setRate(String name, Tags tags, Double value) {
        set(name, tags, value == null ? Double.NaN : value);
    }

    private void set(String name, Tags tags, double value) {
        String key = name + tags;
        AtomicReference<Double> holder = values.computeIfAbsent(key, ignored -> {
            AtomicReference<Double> ref = new AtomicReference<>(value);
            Gauge.builder(name, ref, current -> {
                Double reading = current.get();
                return reading == null ? Double.NaN : reading;
            }).tags(assertAllowed(tags)).register(registry);
            return ref;
        });
        holder.set(value);
    }

    private static Tag tag(String key, String value) {
        if (!ALLOWED_TAGS.contains(key)) {
            throw new IllegalArgumentException("Micrometer tag '" + key + "' is not allowed on online outcome metrics");
        }
        return Tag.of(key, sanitize(value));
    }

    private static String dimensionValue(AttributionDimension dimension) {
        return dimension == null ? "all" : dimension.name();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
