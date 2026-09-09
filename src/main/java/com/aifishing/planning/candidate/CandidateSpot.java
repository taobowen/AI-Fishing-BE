package com.aifishing.planning.candidate;

import com.aifishing.common.enums.TechniqueType;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.strategy.domain.LightPreference;
import com.aifishing.strategy.domain.TechniquePreference;
import com.aifishing.planning.spatial.PathTraversal;
import com.aifishing.planning.spatial.SpatialUtility;
import com.aifishing.planning.spatial.TargetKind;
import com.aifishing.planning.spatial.VisitPortal;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CandidateSpot {

    private UUID featureId;
    private FeatureType type;
    private Geometry sourceGeometry;
    private Point location;
    private Double representativeDepthM;
    private Double minDepthM;
    private Double maxDepthM;
    private Double featureConfidence;
    private double strategyWeight;
    private String strategyRationale;
    private LocalTime windowFrom;
    private LocalTime windowTo;
    private List<TechniquePreference> techniques = List.of();
    private String analysisVersion;
    private Pipeline pipeline;
    private boolean windowSpecific = true;
    private boolean shoreAccessUnverified;
    private boolean secondaryTargetRestricted;
    private boolean windPenalty;
    private Double rawOrientationDeg;
    private LightPreference lightPreference;
    private final List<String> warnings = new ArrayList<>();
    private TargetKind targetKind;
    private Geometry targetGeometry;
    private Geometry fishingCorridor;
    private Point entryPoint;
    private Point exitPoint;
    private Geometry selectedFishingPath;
    private Double fishingCorridorWidthM;
    private UUID fishingTargetId;
    private UUID zoneId;
    private List<VisitPortal> portals = List.of();
    private List<UUID> coverageIds = List.of();
    private List<CandidateSpot> zoneMembers = List.of();
    private UUID visitScopeId;
    private boolean closedLoop;
    private String pathTopology;
    private Double chainageStartM;
    private Double chainageEndM;
    private String splitReason;
    private PathTraversal traversal = PathTraversal.FORWARD;
    private List<SpatialUtility.Sample> staticSamples = List.of();
    private Geometry visitEnvelope;

    public UUID getFeatureId() {
        return featureId;
    }

    public void setFeatureId(UUID featureId) {
        this.featureId = featureId;
    }

    public FeatureType getType() {
        return type;
    }

    public void setType(FeatureType type) {
        this.type = type;
    }

    public Geometry getSourceGeometry() {
        return sourceGeometry;
    }

    public void setSourceGeometry(Geometry sourceGeometry) {
        this.sourceGeometry = sourceGeometry;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public Double getRepresentativeDepthM() {
        return representativeDepthM;
    }

    public void setRepresentativeDepthM(Double representativeDepthM) {
        this.representativeDepthM = representativeDepthM;
    }

    public Double getMinDepthM() {
        return minDepthM;
    }

    public void setMinDepthM(Double minDepthM) {
        this.minDepthM = minDepthM;
    }

    public Double getMaxDepthM() {
        return maxDepthM;
    }

    public void setMaxDepthM(Double maxDepthM) {
        this.maxDepthM = maxDepthM;
    }

    public Double getFeatureConfidence() {
        return featureConfidence;
    }

    public void setFeatureConfidence(Double featureConfidence) {
        this.featureConfidence = featureConfidence;
    }

    public double getStrategyWeight() {
        return strategyWeight;
    }

    public void setStrategyWeight(double strategyWeight) {
        this.strategyWeight = strategyWeight;
    }

    public String getStrategyRationale() {
        return strategyRationale;
    }

    public void setStrategyRationale(String strategyRationale) {
        this.strategyRationale = strategyRationale;
    }

    public LocalTime getWindowFrom() {
        return windowFrom;
    }

    public void setWindowFrom(LocalTime windowFrom) {
        this.windowFrom = windowFrom;
    }

    public LocalTime getWindowTo() {
        return windowTo;
    }

    public void setWindowTo(LocalTime windowTo) {
        this.windowTo = windowTo;
    }

    public List<TechniquePreference> getTechniques() {
        return techniques;
    }

    public void setTechniques(List<TechniquePreference> techniques) {
        this.techniques = techniques == null ? List.of() : List.copyOf(techniques);
    }

    public List<TechniqueType> techniqueTypes() {
        return techniques.stream().map(TechniquePreference::type).toList();
    }

    public String getAnalysisVersion() {
        return analysisVersion;
    }

    public void setAnalysisVersion(String analysisVersion) {
        this.analysisVersion = analysisVersion;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public boolean isWindowSpecific() {
        return windowSpecific;
    }

    public void setWindowSpecific(boolean windowSpecific) {
        this.windowSpecific = windowSpecific;
    }

    public boolean isShoreAccessUnverified() {
        return shoreAccessUnverified;
    }

    public void setShoreAccessUnverified(boolean shoreAccessUnverified) {
        this.shoreAccessUnverified = shoreAccessUnverified;
    }

    public boolean isSecondaryTargetRestricted() {
        return secondaryTargetRestricted;
    }

    public void setSecondaryTargetRestricted(boolean secondaryTargetRestricted) {
        this.secondaryTargetRestricted = secondaryTargetRestricted;
    }

    public boolean isWindPenalty() {
        return windPenalty;
    }

    public void setWindPenalty(boolean windPenalty) {
        this.windPenalty = windPenalty;
    }

    public Double getRawOrientationDeg() {
        return rawOrientationDeg;
    }

    public void setRawOrientationDeg(Double rawOrientationDeg) {
        this.rawOrientationDeg = rawOrientationDeg;
    }

    public LightPreference getLightPreference() {
        return lightPreference == null ? LightPreference.NEUTRAL : lightPreference;
    }

    public void setLightPreference(LightPreference lightPreference) {
        this.lightPreference = lightPreference;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        if (warning != null && !warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    public TargetKind getTargetKind() {
        return targetKind == null ? TargetKind.POINT : targetKind;
    }

    public void setTargetKind(TargetKind targetKind) {
        this.targetKind = targetKind;
    }

    public Geometry getTargetGeometry() {
        return targetGeometry != null ? targetGeometry : sourceGeometry;
    }

    public void setTargetGeometry(Geometry targetGeometry) {
        this.targetGeometry = targetGeometry;
    }

    public Geometry getFishingCorridor() {
        return fishingCorridor;
    }

    public void setFishingCorridor(Geometry fishingCorridor) {
        this.fishingCorridor = fishingCorridor;
    }

    public Point getEntryPoint() {
        return entryPoint != null ? entryPoint : location;
    }

    public void setEntryPoint(Point entryPoint) {
        this.entryPoint = entryPoint;
    }

    public Point getExitPoint() {
        return exitPoint != null ? exitPoint : location;
    }

    public void setExitPoint(Point exitPoint) {
        this.exitPoint = exitPoint;
    }

    public Geometry getSelectedFishingPath() {
        return selectedFishingPath;
    }

    public void setSelectedFishingPath(Geometry selectedFishingPath) {
        this.selectedFishingPath = selectedFishingPath;
    }

    public Double getFishingCorridorWidthM() {
        return fishingCorridorWidthM;
    }

    public void setFishingCorridorWidthM(Double fishingCorridorWidthM) {
        this.fishingCorridorWidthM = fishingCorridorWidthM;
    }

    public UUID getFishingTargetId() {
        return fishingTargetId;
    }

    public void setFishingTargetId(UUID fishingTargetId) {
        this.fishingTargetId = fishingTargetId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public List<VisitPortal> getPortals() {
        return portals;
    }

    public void setPortals(List<VisitPortal> portals) {
        this.portals = portals == null ? List.of() : List.copyOf(portals);
    }

    public List<UUID> coverageIds() {
        if (coverageIds != null && !coverageIds.isEmpty()) {
            return coverageIds;
        }
        if (fishingTargetId != null) {
            return List.of(fishingTargetId);
        }
        return featureId == null ? List.of() : List.of(featureId);
    }

    public void setCoverageIds(List<UUID> coverageIds) {
        this.coverageIds = coverageIds == null ? List.of() : List.copyOf(coverageIds);
    }

    public List<CandidateSpot> getZoneMembers() {
        return zoneMembers;
    }

    public void setZoneMembers(List<CandidateSpot> zoneMembers) {
        this.zoneMembers = zoneMembers == null ? List.of() : List.copyOf(zoneMembers);
    }

    public UUID getVisitScopeId() {
        return visitScopeId;
    }

    public void setVisitScopeId(UUID visitScopeId) {
        this.visitScopeId = visitScopeId;
    }

    public boolean isClosedLoop() {
        return closedLoop;
    }

    public void setClosedLoop(boolean closedLoop) {
        this.closedLoop = closedLoop;
    }

    public String getPathTopology() {
        return pathTopology;
    }

    public void setPathTopology(String pathTopology) {
        this.pathTopology = pathTopology;
    }

    public Double getChainageStartM() {
        return chainageStartM;
    }

    public void setChainageStartM(double chainageStartM) {
        this.chainageStartM = chainageStartM;
    }

    public Double getChainageEndM() {
        return chainageEndM;
    }

    public void setChainageEndM(double chainageEndM) {
        this.chainageEndM = chainageEndM;
    }

    public String getSplitReason() {
        return splitReason;
    }

    public void setSplitReason(String splitReason) {
        this.splitReason = splitReason;
    }

    public PathTraversal getTraversal() {
        return traversal == null ? PathTraversal.FORWARD : traversal;
    }

    public void setTraversal(PathTraversal traversal) {
        this.traversal = traversal == null ? PathTraversal.FORWARD : traversal;
    }

    public List<SpatialUtility.Sample> getStaticSamples() {
        return staticSamples;
    }

    public void setStaticSamples(List<SpatialUtility.Sample> staticSamples) {
        this.staticSamples = staticSamples == null ? List.of() : List.copyOf(staticSamples);
    }

    public Geometry getVisitEnvelope() {
        return visitEnvelope;
    }

    public void setVisitEnvelope(Geometry visitEnvelope) {
        this.visitEnvelope = visitEnvelope;
    }

    public UUID planningIdentity() {
        if (getTargetKind() == TargetKind.ZONE && visitScopeId != null) {
            return visitScopeId;
        }
        if (fishingTargetId != null) {
            return fishingTargetId;
        }
        return featureId;
    }
}
