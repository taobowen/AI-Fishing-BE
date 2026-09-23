package com.aifishing.planning.candidate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded Beam input after launch/boat/spatial context.
 * Unassigned buckets are shortlist diversity groups only — not PhysicalZones.
 */
public record MacroCandidateWorld(
        List<PhysicalZoneOption> physicalZones,
        List<UnassignedBucket> unassignedBuckets,
        List<CandidateSpot> beamSpots,
        Map<String, Integer> macroOptionsByRegion
) {
    public MacroCandidateWorld {
        physicalZones = physicalZones == null ? List.of() : List.copyOf(physicalZones);
        unassignedBuckets = unassignedBuckets == null ? List.of() : List.copyOf(unassignedBuckets);
        beamSpots = beamSpots == null ? List.of() : List.copyOf(beamSpots);
        macroOptionsByRegion = macroOptionsByRegion == null ? Map.of() : Map.copyOf(macroOptionsByRegion);
    }

    public record PhysicalZoneOption(
            UUID zoneId,
            CandidateSpot zoneSpot,
            List<CandidateSpot> macroRepresentatives,
            List<CandidateSpot> reachableZoneMembers,
            String regionKey
    ) {
        public PhysicalZoneOption {
            macroRepresentatives = macroRepresentatives == null ? List.of() : List.copyOf(macroRepresentatives);
            reachableZoneMembers = reachableZoneMembers == null ? List.of() : List.copyOf(reachableZoneMembers);
        }
    }

    public record UnassignedBucket(
            String regionKey,
            List<CandidateSpot> representativeAtomics
    ) {
        public UnassignedBucket {
            representativeAtomics = representativeAtomics == null ? List.of() : List.copyOf(representativeAtomics);
        }
    }
}
