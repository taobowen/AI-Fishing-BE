package com.aifishing.boat.capability;

import com.aifishing.common.enums.CapabilitySource;

public record CapabilityMetric<T>(
        T value,
        Double confidence,
        CapabilitySource source
) {
    public static <T> CapabilityMetric<T> of(T value, Double confidence, CapabilitySource source) {
        return new CapabilityMetric<>(value, confidence, source);
    }

    public boolean present() {
        return value != null;
    }
}
