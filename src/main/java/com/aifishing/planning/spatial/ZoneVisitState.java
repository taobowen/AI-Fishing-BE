package com.aifishing.planning.spatial;

import com.aifishing.planning.candidate.CandidateSpot;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Incremental consumption of a PhysicalZone. One visit does not exhaust the zone.
 * Future: Opportunity Revisit Cooldown should apply per consumed member after leaving,
 * not as a coarse zone-wide cooldown, and never on EXTEND.
 */
public record ZoneVisitState(
        Set<UUID> membersFished,
        int fishingMinutesSpent,
        double localDistanceCoveredM,
        int entries,
        Instant lastVisitTime,
        String lastLocalSequenceFingerprint
) {
    public ZoneVisitState {
        membersFished = membersFished == null ? Set.of() : Set.copyOf(membersFished);
        lastLocalSequenceFingerprint = lastLocalSequenceFingerprint == null ? "" : lastLocalSequenceFingerprint;
    }

    public static ZoneVisitState empty() {
        return new ZoneVisitState(Set.of(), 0, 0, 0, null, "");
    }

    public List<CandidateSpot> remainingMembers(CandidateSpot zone) {
        if (zone == null || zone.getZoneMembers() == null) {
            return List.of();
        }
        return zone.getZoneMembers().stream()
                .filter(member -> !membersFished.contains(memberId(member)))
                .toList();
    }

    public ZoneVisitState addEntry(ZoneFishingPackage pkg, Instant at) {
        if (pkg == null) {
            return this;
        }
        Set<UUID> nextMembers = new LinkedHashSet<>(membersFished);
        nextMembers.addAll(pkg.consumedMemberIds());
        return new ZoneVisitState(
                nextMembers,
                fishingMinutesSpent + pkg.fishingMinutes(),
                localDistanceCoveredM + pkg.localDistanceM(),
                entries + 1,
                at,
                pkg.sequenceFingerprint()
        );
    }

    public ZoneVisitState replaceCurrentEntry(ZoneFishingPackage previous, ZoneFishingPackage next, Instant at) {
        if (next == null) {
            return this;
        }
        Set<UUID> nextMembers = new LinkedHashSet<>(membersFished);
        if (previous != null) {
            nextMembers.removeAll(previous.consumedMemberIds());
        }
        nextMembers.addAll(next.consumedMemberIds());
        int fishing = fishingMinutesSpent - (previous == null ? 0 : previous.fishingMinutes()) + next.fishingMinutes();
        double distance = localDistanceCoveredM - (previous == null ? 0 : previous.localDistanceM()) + next.localDistanceM();
        return new ZoneVisitState(
                nextMembers,
                Math.max(0, fishing),
                Math.max(0, distance),
                entries,
                at,
                next.sequenceFingerprint()
        );
    }

    public static UUID memberId(CandidateSpot member) {
        if (member == null) {
            return null;
        }
        return member.getFishingTargetId() == null ? member.getFeatureId() : member.getFishingTargetId();
    }
}
