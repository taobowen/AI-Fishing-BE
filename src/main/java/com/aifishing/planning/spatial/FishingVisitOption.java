package com.aifishing.planning.spatial;

import com.aifishing.planning.ranking.RankedCandidate;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.UUID;

public record FishingVisitOption(
        RankedCandidate candidate,
        VisitPortal entry,
        VisitPortal exit,
        String optionId,
        VisitOptionKey key,
        PathTraversal traversal
) {
    public FishingVisitOption(RankedCandidate candidate, VisitPortal entry, VisitPortal exit, String optionId) {
        this(candidate, entry, exit, optionId, null, PathTraversal.FORWARD);
    }

    public Point entryPoint() {
        return entry == null ? candidate.spot().getEntryPoint() : entry.point();
    }

    public Point exitPoint() {
        return exit == null ? candidate.spot().getExitPoint() : exit.point();
    }

    public List<UUID> coverageIds() {
        return candidate.spot().coverageIds();
    }

    public TargetKind kind() {
        return candidate.spot().getTargetKind();
    }
}
