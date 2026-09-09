package com.aifishing.webquota.dto;

public record WebPlanEntitlementResponse(
        int limit,
        int used,
        int remaining
) {
}
