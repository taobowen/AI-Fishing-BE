package com.aifishing.planning.intent;

import com.aifishing.fishingtemplate.domain.FishingTemplate;
import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.fishingtemplate.repo.FishingTemplateRepository;
import com.aifishing.fishingtemplate.repo.FishingTemplateTargetRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import com.aifishing.planning.domain.PlanningInputTargetSource;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.domain.TripRequiredPoint;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.trip.repo.TripRequiredPointRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IntentSpatialResolutionService implements IntentMatchSource {

    private final PlanningProperties properties;
    private final IntentSpatialResolutionRepository liveRepository;
    private final TripPlanningIntentResolutionRepository runRepository;
    private final FishingTemplateRepository templateRepository;
    private final FishingTemplateTargetRepository templateTargetRepository;
    private final TripRequiredPointRepository requiredPointRepository;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final LakePlanningGeometryLoader geometryLoader;
    private final SpatialPlanningSnapshotRepository snapshotRepository;
    private final LakeFishingTargetRepository fishingTargetRepository;

    public IntentSpatialResolutionService(
            PlanningProperties properties,
            IntentSpatialResolutionRepository liveRepository,
            TripPlanningIntentResolutionRepository runRepository,
            FishingTemplateRepository templateRepository,
            FishingTemplateTargetRepository templateTargetRepository,
            TripRequiredPointRepository requiredPointRepository,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            LakePlanningGeometryLoader geometryLoader,
            SpatialPlanningSnapshotRepository snapshotRepository,
            LakeFishingTargetRepository fishingTargetRepository
    ) {
        this.properties = properties;
        this.liveRepository = liveRepository;
        this.runRepository = runRepository;
        this.templateRepository = templateRepository;
        this.templateTargetRepository = templateTargetRepository;
        this.requiredPointRepository = requiredPointRepository;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.geometryLoader = geometryLoader;
        this.snapshotRepository = snapshotRepository;
        this.fishingTargetRepository = fishingTargetRepository;
    }

    @Override
    @Transactional
    public List<IntentMatch> matches(TripPlanningInputTarget target, PlanningContext context) {
        if (target == null || context == null || context.spatialSnapshot() == null) {
            return List.of();
        }
        TemplateTargetKind kind = target.getKind();
        if (kind == null || kind == TemplateTargetKind.ZONE) {
            return List.of();
        }
        IntentOriginKind originKind = target.getSource() == PlanningInputTargetSource.REQUIRED_POINT
                ? IntentOriginKind.REQUIRED_POINT
                : IntentOriginKind.TEMPLATE_TARGET;
        UUID originId = originKind == IntentOriginKind.REQUIRED_POINT
                ? target.getOriginRequiredPointId()
                : target.getOriginTemplateTargetId();
        IntentMatchConfig config = IntentMatchConfig.from(properties.getSpatial());
        UUID snapshotId = context.spatialSnapshot().id();
        List<IntentMatch> matches;
        if (originId == null) {
            matches = List.of(IntentMatch.synthetic());
        } else {
            List<IntentSpatialResolution> cached = findLive(originKind, originId, snapshotId, config);
            if (!cached.isEmpty()) {
                matches = cached.stream().map(IntentSpatialResolution::toMatch).toList();
            } else {
                double radius = originKind == IntentOriginKind.REQUIRED_POINT
                        ? config.requiredPointRadiusM()
                        : kind == TemplateTargetKind.PATH
                        ? config.templatePathCorridorM()
                        : config.templatePointRadiusM();
                matches = IntentEnvelopeMatcher.match(
                        target.getGeometry(),
                        kind,
                        radius,
                        config.maxMatches(),
                        context.spatialSnapshot().targets(),
                        context.geometry()
                );
                replaceLive(originKind, originId, snapshotId, config, matches);
            }
        }
        recordRun(target, originKind, snapshotId, config, matches);
        return matches;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveTemplateTargets(List<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }
        for (UUID id : targetIds) {
            FishingTemplateTarget target = templateTargetRepository.findById(id).orElse(null);
            if (target == null || target.getKind() == TemplateTargetKind.ZONE) {
                continue;
            }
            FishingTemplate template = templateRepository.findById(target.getTemplateId()).orElse(null);
            if (template == null) {
                continue;
            }
            resolveLive(
                    IntentOriginKind.TEMPLATE_TARGET,
                    target.getId(),
                    null,
                    template.getLakeId(),
                    target.getKind(),
                    target.getGeometry()
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolveRequiredPoints(List<UUID> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return;
        }
        for (UUID id : pointIds) {
            TripRequiredPoint point = requiredPointRepository.findById(id).orElse(null);
            if (point == null) {
                continue;
            }
            Trip trip = tripRepository.findById(point.getTripId()).orElse(null);
            if (trip == null) {
                continue;
            }
            resolveLive(
                    IntentOriginKind.REQUIRED_POINT,
                    null,
                    point.getId(),
                    trip.getLakeId(),
                    TemplateTargetKind.POINT,
                    point.getLocation()
            );
        }
    }

    private void resolveLive(
            IntentOriginKind kind,
            UUID templateTargetId,
            UUID requiredPointId,
            UUID lakeId,
            TemplateTargetKind geometryKind,
            Geometry geometry
    ) {
        Lake lake = lakeRepository.findById(lakeId).orElse(null);
        if (lake == null) {
            return;
        }
        PlanningProperties.Spatial spatial = properties.getSpatial();
        List<SpatialPlanningSnapshot> ready = snapshotRepository
                .findByLakeIdAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersionAndStatusOrderByCreatedAtDesc(
                        lakeId,
                        spatial.getDerivationVersion(),
                        spatial.getZoneBuilderVersion(),
                        spatial.getNavigationVersion(),
                        SpatialSnapshotStatus.READY
                );
        if (ready.isEmpty()) {
            return;
        }
        UUID snapshotId = ready.get(0).getId();
        List<LakeFishingTarget> catalog = fishingTargetRepository.findBySpatialPlanningSnapshotId(snapshotId);
        LakePlanningGeometry lakeGeometry = geometryLoader.load(lake);
        IntentMatchConfig config = IntentMatchConfig.from(spatial);
        double radius = kind == IntentOriginKind.REQUIRED_POINT
                ? config.requiredPointRadiusM()
                : geometryKind == TemplateTargetKind.PATH
                ? config.templatePathCorridorM()
                : config.templatePointRadiusM();
        List<IntentMatch> matches = IntentEnvelopeMatcher.match(
                geometry,
                geometryKind,
                radius,
                config.maxMatches(),
                catalog,
                lakeGeometry
        );
        UUID originId = kind == IntentOriginKind.REQUIRED_POINT ? requiredPointId : templateTargetId;
        replaceLive(kind, originId, snapshotId, config, matches);
    }

    private List<IntentSpatialResolution> findLive(
            IntentOriginKind kind,
            UUID originId,
            UUID snapshotId,
            IntentMatchConfig config
    ) {
        if (kind == IntentOriginKind.REQUIRED_POINT) {
            return liveRepository.findRequired(
                    originId,
                    snapshotId,
                    config.matchingVersion(),
                    config.templatePointRadiusM(),
                    config.templatePathCorridorM(),
                    config.requiredPointRadiusM(),
                    config.maxMatches()
            );
        }
        return liveRepository.findTemplate(
                originId,
                snapshotId,
                config.matchingVersion(),
                config.templatePointRadiusM(),
                config.templatePathCorridorM(),
                config.requiredPointRadiusM(),
                config.maxMatches()
        );
    }

    private void replaceLive(
            IntentOriginKind kind,
            UUID originId,
            UUID snapshotId,
            IntentMatchConfig config,
            List<IntentMatch> matches
    ) {
        if (kind == IntentOriginKind.REQUIRED_POINT) {
            liveRepository.deleteRequired(
                    originId,
                    snapshotId,
                    config.matchingVersion(),
                    config.templatePointRadiusM(),
                    config.templatePathCorridorM(),
                    config.requiredPointRadiusM(),
                    config.maxMatches()
            );
        } else {
            liveRepository.deleteTemplate(
                    originId,
                    snapshotId,
                    config.matchingVersion(),
                    config.templatePointRadiusM(),
                    config.templatePathCorridorM(),
                    config.requiredPointRadiusM(),
                    config.maxMatches()
            );
        }
        List<IntentSpatialResolution> rows = new ArrayList<>();
        for (IntentMatch match : matches) {
            IntentSpatialResolution row = new IntentSpatialResolution();
            row.setOriginKind(kind);
            if (kind == IntentOriginKind.REQUIRED_POINT) {
                row.setOriginRequiredPointId(originId);
            } else {
                row.setOriginTemplateTargetId(originId);
            }
            row.setSpatialSnapshotId(snapshotId);
            row.setMatchingVersion(config.matchingVersion());
            row.setTemplatePointRadiusM(config.templatePointRadiusM());
            row.setTemplatePathCorridorM(config.templatePathCorridorM());
            row.setRequiredPointRadiusM(config.requiredPointRadiusM());
            row.setMaxMatches(config.maxMatches());
            row.setRank(match.rank());
            row.setFishingTargetId(match.fishingTargetId());
            row.setFeatureId(match.featureId());
            row.setDistanceM(match.distanceM());
            row.setOverlapM(match.overlapM());
            row.setSyntheticFallback(match.syntheticFallback());
            rows.add(row);
        }
        liveRepository.saveAll(rows);
    }

    private void recordRun(
            TripPlanningInputTarget target,
            IntentOriginKind kind,
            UUID snapshotId,
            IntentMatchConfig config,
            List<IntentMatch> matches
    ) {
        if (target.getId() == null
                || !runRepository.findByPlanningInputTargetIdOrderByRankAsc(target.getId()).isEmpty()) {
            return;
        }
        List<TripPlanningIntentResolution> rows = new ArrayList<>();
        for (IntentMatch match : matches) {
            TripPlanningIntentResolution row = new TripPlanningIntentResolution();
            row.setPlanningInputTargetId(target.getId());
            row.setOriginKind(kind);
            row.setOriginTemplateTargetId(target.getOriginTemplateTargetId());
            row.setOriginRequiredPointId(target.getOriginRequiredPointId());
            row.setSpatialSnapshotId(snapshotId);
            row.setMatchingVersion(config.matchingVersion());
            row.setTemplatePointRadiusM(config.templatePointRadiusM());
            row.setTemplatePathCorridorM(config.templatePathCorridorM());
            row.setRequiredPointRadiusM(config.requiredPointRadiusM());
            row.setMaxMatches(config.maxMatches());
            row.setRank(match.rank());
            row.setFishingTargetId(match.fishingTargetId());
            row.setFeatureId(match.featureId());
            row.setDistanceM(match.distanceM());
            row.setOverlapM(match.overlapM());
            row.setSyntheticFallback(match.syntheticFallback());
            rows.add(row);
        }
        runRepository.saveAll(rows);
    }
}
