package com.aifishing.guidance.metrics;

import com.aifishing.guidance.contracts.OnlineMetricGrain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class OnlineMetricWindows {

    private OnlineMetricWindows() {
    }

    public static Instant start(Instant at, OnlineMetricGrain grain) {
        Instant instant = at == null ? Instant.EPOCH : at;
        if (grain == OnlineMetricGrain.DAY) {
            return instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        }
        return instant.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS).toInstant();
    }

    public static Instant end(Instant start, OnlineMetricGrain grain) {
        Instant from = start == null ? Instant.EPOCH : start;
        if (grain == OnlineMetricGrain.DAY) {
            return from.plus(1, ChronoUnit.DAYS);
        }
        return from.plus(1, ChronoUnit.HOURS);
    }
}
