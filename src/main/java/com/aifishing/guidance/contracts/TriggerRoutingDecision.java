package com.aifishing.guidance.contracts;

import java.util.List;
import java.util.Objects;

/**
 * Primary plus related derived triggers. {@code reasonCodes} are diagnostic only
 * and must not encode {@link GuidanceAction} values.
 */
public record TriggerRoutingDecision(
        GuidanceTrigger primary,
        List<GuidanceTrigger> related,
        List<String> reasonCodes
) {
    public TriggerRoutingDecision {
        Objects.requireNonNull(primary, "primary");
        related = List.copyOf(related == null ? List.of() : related);
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        for (String code : reasonCodes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("reasonCodes must be non-blank diagnostic codes");
            }
            for (GuidanceAction action : GuidanceAction.values()) {
                if (action.name().equals(code)) {
                    throw new IllegalArgumentException("reasonCodes must not encode actions");
                }
            }
        }
    }
}
