package com.aifishing.guidance.tools;

import com.aifishing.guidance.contracts.GetWaypointStructureParams;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.ToolName;
import com.aifishing.guidance.contracts.ToolRequestEnvelope;
import com.aifishing.guidance.contracts.ToolResultEnvelope;
import com.aifishing.guidance.spi.AgentTool;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.domain.TripWaypointPlanMetadata;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.spatial.CastingOpportunity;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.TripStopSubtarget;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.TripStopSubtargetRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Structure fields from a trip waypoint, its lake feature, and optional spatial target.
 */
@Component
public class GetWaypointStructureTool implements AgentTool {

    private final TripWaypointRepository tripWaypointRepository;
    private final LakeFeatureRepository lakeFeatureRepository;
    private final LakeFishingTargetRepository fishingTargetRepository;
    private final TripStopSubtargetRepository subtargetRepository;
    private final Clock clock;

    public GetWaypointStructureTool(
            TripWaypointRepository tripWaypointRepository,
            LakeFeatureRepository lakeFeatureRepository,
            LakeFishingTargetRepository fishingTargetRepository,
            Clock clock
    ) {
        this(tripWaypointRepository, lakeFeatureRepository, fishingTargetRepository, null, clock);
    }

    @Autowired
    public GetWaypointStructureTool(
            TripWaypointRepository tripWaypointRepository,
            LakeFeatureRepository lakeFeatureRepository,
            LakeFishingTargetRepository fishingTargetRepository,
            TripStopSubtargetRepository subtargetRepository,
            Clock clock
    ) {
        this.tripWaypointRepository = tripWaypointRepository;
        this.lakeFeatureRepository = lakeFeatureRepository;
        this.fishingTargetRepository = fishingTargetRepository;
        this.subtargetRepository = subtargetRepository;
        this.clock = clock;
    }

    @Override
    public ToolName name() {
        return ToolName.GET_WAYPOINT_STRUCTURE;
    }

    @Override
    public ToolResultEnvelope execute(ToolRequestEnvelope request) {
        AgentToolSupport.Parsed<GetWaypointStructureParams> parsed = AgentToolSupport.parse(
                request, name(), "GetWaypointStructureParams", GetWaypointStructureParams.class, clock);
        if (!parsed.valid()) {
            return parsed.error();
        }
        try {
            TripWaypoint waypoint = tripWaypointRepository.findById(parsed.params().tripWaypointId()).orElse(null);
            if (waypoint == null) {
                return AgentToolSupport.unknown(name(), clock);
            }
            LakeFeature feature = waypoint.getLakeFeatureId() == null
                    ? null
                    : lakeFeatureRepository.findById(waypoint.getLakeFeatureId()).orElse(null);
            LakeFishingTarget target = waypoint.getFishingTargetId() == null
                    ? null
                    : fishingTargetRepository.findById(waypoint.getFishingTargetId()).orElse(null);

            ObjectNode data = GuidanceContracts.mapper().createObjectNode();
            data.put("tripWaypointId", waypoint.getId().toString());
            put(data, "featureType", firstNonNull(
                    waypoint.getFeatureType() == null ? null : waypoint.getFeatureType().name(),
                    feature == null || feature.getType() == null ? null : feature.getType().name(),
                    target == null || target.getSemanticType() == null ? null : target.getSemanticType().name()
            ));
            put(data, "targetKind", firstNonNull(
                    waypoint.getTargetKind() == null ? null : waypoint.getTargetKind().name(),
                    target == null || target.getTargetKind() == null ? null : target.getTargetKind().name()
            ));
            put(data, "minDepthM", firstNonNull(
                    AgentToolSupport.decimal(waypoint.getMinDepthM()),
                    feature == null ? null : AgentToolSupport.decimal(feature.getMinDepthM()),
                    target == null ? null : AgentToolSupport.decimal(target.getMinDepthM())
            ));
            put(data, "maxDepthM", firstNonNull(
                    AgentToolSupport.decimal(waypoint.getMaxDepthM()),
                    feature == null ? null : AgentToolSupport.decimal(feature.getMaxDepthM()),
                    target == null ? null : AgentToolSupport.decimal(target.getMaxDepthM())
            ));
            put(data, "representativeDepthM", firstNonNull(
                    AgentToolSupport.decimal(waypoint.getRepresentativeDepthM()),
                    target == null ? null : AgentToolSupport.decimal(target.getRepresentativeDepthM())
            ));
            put(data, "slope", feature == null ? null : AgentToolSupport.decimal(feature.getSlope()));
            put(data, "orientation", feature == null ? null : AgentToolSupport.decimal(feature.getOrientation()));
            put(data, "areaM2", feature == null ? null : AgentToolSupport.decimal(feature.getAreaM2()));
            if (waypoint.getLakeFeatureId() != null) {
                data.put("lakeFeatureId", waypoint.getLakeFeatureId().toString());
            }
            if (waypoint.getFishingTargetId() != null) {
                data.put("fishingTargetId", waypoint.getFishingTargetId().toString());
            }
            if (waypoint.getZoneId() != null) {
                data.put("zoneId", waypoint.getZoneId().toString());
            }
            putUuidArray(data, "packageMemberIds", TripWaypointPlanMetadata.packageMemberIds(waypoint));
            putCastingOpportunities(data, waypoint.getId());
            if (!hasStructure(data)) {
                return AgentToolSupport.unknown(name(), clock);
            }
            return AgentToolSupport.ok(name(), clock, data);
        } catch (RuntimeException ex) {
            return AgentToolSupport.error(name(), clock);
        }
    }

    private void putCastingOpportunities(ObjectNode data, UUID waypointId) {
        if (subtargetRepository == null || waypointId == null) {
            return;
        }
        List<TripStopSubtarget> rows = subtargetRepository.findByTripWaypointIdOrderBySequenceAsc(waypointId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        ArrayNode opportunities = data.putArray("castingOpportunities");
        ObjectNode current = null;
        ArrayNode companions = null;
        for (TripStopSubtarget row : rows) {
            if (row == null || row.getFishingTargetId() == null) {
                continue;
            }
            if (CastingOpportunity.companionReason(row.getReason()) && current != null) {
                companions.add(row.getFishingTargetId().toString());
                continue;
            }
            current = opportunities.addObject();
            current.put("anchorFishingTargetId", row.getFishingTargetId().toString());
            if (row.getPlannedFishingMinutes() != null) {
                current.put("dwellMinutes", row.getPlannedFishingMinutes());
            }
            companions = current.putArray("companionFishingTargetIds");
        }
        if (opportunities.isEmpty()) {
            data.remove("castingOpportunities");
            return;
        }
        data.put(
                "castingOpportunityRule",
                "Companions share the anchor dwell at this same stop. Trying a companion is not a new stop and is not another full dwell."
        );
    }

    private static boolean hasStructure(ObjectNode data) {
        return data.has("featureType")
                || data.has("targetKind")
                || data.has("minDepthM")
                || data.has("maxDepthM")
                || data.has("representativeDepthM")
                || data.has("slope")
                || data.has("orientation")
                || data.has("areaM2");
    }

    private static void put(ObjectNode data, String field, String value) {
        if (value != null && !value.isBlank()) {
            data.put(field, value);
        }
    }

    private static void put(ObjectNode data, String field, Double value) {
        if (value != null) {
            data.put(field, value);
        }
    }

    private static void putUuidArray(ObjectNode data, String field, List<UUID> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ArrayNode array = data.putArray(field);
        for (UUID value : values) {
            if (value != null) {
                array.add(value.toString());
            }
        }
        if (array.isEmpty()) {
            data.remove(field);
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
