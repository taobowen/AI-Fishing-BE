package com.aifishing.planning.route;

import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.ZoneFishingPackage;
import com.aifishing.planning.spatial.ZoneVisitState;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Beam consumption state. Visited is not exhausted.
 * POINT/PATH identities are consumed once per plan; extra time is EXTEND of the current dwell.
 * ZONE members are consumed incrementally; maxZoneEntries is a temporary hard guard.
 * <p>
 * Future enhancement — Opportunity Revisit Cooldown (not implemented): after leaving a
 * consumed physical opportunity, re-eligibility should wait a type-aware cooldown
 * (POINT vs PATH vs member-level ZONE). Do not cooldown an entire PhysicalZone when
 * only some members were fished. EXTEND is continuous fishing, not a cooldown event.
 * Re-entry after cooldown must still use marginal / diminishing utility and must not
 * double-count prior consumption.
 */
public final class RouteOpportunityState {

    private final Set<UUID> consumedAtomics;
    private final Map<UUID, ZoneVisitState> zones;

    private RouteOpportunityState(Set<UUID> consumedAtomics, Map<UUID, ZoneVisitState> zones) {
        this.consumedAtomics = Set.copyOf(consumedAtomics);
        this.zones = Map.copyOf(zones);
    }

    public static RouteOpportunityState empty() {
        return new RouteOpportunityState(Set.of(), Map.of());
    }

    public Set<UUID> consumedAtomics() {
        return consumedAtomics;
    }

    public ZoneVisitState zone(UUID zoneId) {
        if (zoneId == null) {
            return ZoneVisitState.empty();
        }
        return zones.getOrDefault(zoneId, ZoneVisitState.empty());
    }

    public int entries(UUID zoneId) {
        return zone(zoneId).entries();
    }

    public boolean atomicConsumed(UUID identity) {
        return identity != null && consumedAtomics.contains(identity);
    }

    public static UUID zoneIdentity(CandidateSpot spot) {
        if (spot == null) {
            return null;
        }
        if (spot.getZoneId() != null) {
            return spot.getZoneId();
        }
        return spot.planningIdentity();
    }

    public static UUID atomicIdentity(CandidateSpot spot) {
        return spot == null ? null : spot.planningIdentity();
    }

    public boolean canEnterZone(UUID zoneId, int maxZoneEntries, boolean currentlyThere) {
        if (currentlyThere) {
            return true;
        }
        return entries(zoneId) < Math.max(1, maxZoneEntries);
    }

    public RouteOpportunityState consumeAtomic(UUID identity) {
        if (identity == null || consumedAtomics.contains(identity)) {
            return this;
        }
        Set<UUID> next = new HashSet<>(consumedAtomics);
        next.add(identity);
        return new RouteOpportunityState(next, zones);
    }

    public RouteOpportunityState applyZoneEntry(UUID zoneId, ZoneFishingPackage pkg, Instant at) {
        if (zoneId == null) {
            return this;
        }
        Map<UUID, ZoneVisitState> next = new HashMap<>(zones);
        next.put(zoneId, zone(zoneId).addEntry(pkg, at));
        return new RouteOpportunityState(consumedAtomics, next);
    }

    public RouteOpportunityState replaceZoneEntry(
            UUID zoneId,
            ZoneFishingPackage previous,
            ZoneFishingPackage nextPkg,
            Instant at
    ) {
        if (zoneId == null) {
            return this;
        }
        Map<UUID, ZoneVisitState> next = new HashMap<>(zones);
        next.put(zoneId, zone(zoneId).replaceCurrentEntry(previous, nextPkg, at));
        return new RouteOpportunityState(consumedAtomics, next);
    }

    public boolean membersRemain(CandidateSpot zone) {
        return !zone(zoneIdentity(zone)).remainingMembers(zone).isEmpty();
    }

    public String consumptionFingerprint() {
        String atomics = consumedAtomics.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        String zonePart = zones.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    TreeSet<String> members = new TreeSet<>();
                    for (UUID member : entry.getValue().membersFished()) {
                        members.add(member.toString());
                    }
                    return entry.getKey() + ":" + String.join(",", members);
                })
                .collect(Collectors.joining(";"));
        return atomics + "|" + zonePart;
    }

    public static boolean isZone(CandidateSpot spot) {
        return spot != null && spot.getTargetKind() == TargetKind.ZONE;
    }
}
