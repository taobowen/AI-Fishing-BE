package com.aifishing.planning.spatial;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable planning/cache key. Never Java object identity.
 */
public record VisitOptionKey(
        UUID targetOrPhysicalZoneId,
        UUID visitScopeId,
        String entryPortalId,
        String exitPortalId,
        String traversalKey,
        int arrivalSlot,
        int dwellOption
) {
    public VisitOptionKey {
        entryPortalId = entryPortalId == null ? "" : entryPortalId;
        exitPortalId = exitPortalId == null ? "" : exitPortalId;
        traversalKey = traversalKey == null || traversalKey.isBlank() ? PathTraversal.FORWARD.name() : traversalKey;
    }

    public static VisitOptionKey of(
            UUID targetOrPhysicalZoneId,
            UUID visitScopeId,
            VisitPortal entry,
            VisitPortal exit,
            PathTraversal traversal,
            String partialSpan,
            int arrivalSlot,
            int dwellOption
    ) {
        String traversalKey = traversal == null ? PathTraversal.FORWARD.name() : traversal.name();
        if (partialSpan != null && !partialSpan.isBlank()) {
            traversalKey = traversalKey + ":" + partialSpan;
        }
        return new VisitOptionKey(
                targetOrPhysicalZoneId,
                visitScopeId,
                entry == null ? "" : entry.id(),
                exit == null ? "" : exit.id(),
                traversalKey,
                arrivalSlot,
                dwellOption
        );
    }

    public String cacheKey() {
        return String.join("|",
                String.valueOf(targetOrPhysicalZoneId),
                String.valueOf(visitScopeId),
                entryPortalId,
                exitPortalId,
                traversalKey,
                Integer.toString(arrivalSlot),
                Integer.toString(dwellOption));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VisitOptionKey that)) {
            return false;
        }
        return arrivalSlot == that.arrivalSlot
                && dwellOption == that.dwellOption
                && Objects.equals(targetOrPhysicalZoneId, that.targetOrPhysicalZoneId)
                && Objects.equals(visitScopeId, that.visitScopeId)
                && Objects.equals(entryPortalId, that.entryPortalId)
                && Objects.equals(exitPortalId, that.exitPortalId)
                && Objects.equals(traversalKey, that.traversalKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                targetOrPhysicalZoneId,
                visitScopeId,
                entryPortalId,
                exitPortalId,
                traversalKey,
                arrivalSlot,
                dwellOption);
    }
}
