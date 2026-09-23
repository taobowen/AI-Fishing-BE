package com.aifishing.guidance.contracts;

/**
 * Persistence grain for {@code guidance_online_metric_rollups}. Rates are derived
 * after SUM, never stored.
 */
public enum OnlineMetricGrain {
    HOUR,
    DAY
}
