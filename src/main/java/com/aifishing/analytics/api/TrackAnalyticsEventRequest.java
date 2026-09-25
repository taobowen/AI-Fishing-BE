package com.aifishing.analytics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record TrackAnalyticsEventRequest(
        @NotBlank @Size(max = 128) String name,
        Map<String, Object> properties
) {
}
