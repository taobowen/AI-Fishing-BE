package com.aifishing.planning.service;

import com.aifishing.auth.ClientIdentity;
import com.aifishing.auth.CurrentUser;
import com.aifishing.common.enums.CandidateSource;
import com.aifishing.common.enums.ClientChannel;
import com.aifishing.boat.capability.BoatCapabilityResolver;
import com.aifishing.boat.capability.EffectiveBoatCapability;
import com.aifishing.boat.capability.EffectiveBoatCapabilityFactory;
import com.aifishing.boat.capability.ResolvedBoatCapability;
import com.aifishing.boat.domain.Boat;
import com.aifishing.boat.repo.BoatRepository;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.GearType;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.exception.NotFoundException;
import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.feedback.ranking.EmpiricalRankingProvider;
import com.aifishing.gear.repo.GearRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.domain.LakeWaterway;
import com.aifishing.lake.ingestion.dto.DatasetStatusCode;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.FishingRestrictionRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.dto.Pipeline;
import com.aifishing.lake.processing.extract.GeoMetrics;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.lake.processing.service.StructurePipelineReadiness;
import com.aifishing.lake.processing.service.StructurePipelineReadinessService;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.launch.ResolvedTripLaunch;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateGenerator;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.MacroCandidateShortlist;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import com.aifishing.planning.candidate.PlanningCandidatePool;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.planning.domain.TripWaypoint;
import com.aifishing.planning.dto.GeneratePlanRequest;
import com.aifishing.planning.dto.GeneratePlanResponse;
import com.aifishing.planning.dto.PlanningBalanceResponse;
import com.aifishing.planning.dto.PlanningRunResponse;
import com.aifishing.planning.dto.PlanningRunSummaryResponse;
import com.aifishing.planning.dto.TripPlanMapDataResponse;
import com.aifishing.planning.dto.TripPlanResponse;
import com.aifishing.planning.filter.AccessibilityFilter;
import com.aifishing.planning.filter.BoatCapabilityFilter;
import com.aifishing.planning.filter.CandidateFilter;
import com.aifishing.planning.filter.FilterResult;
import com.aifishing.planning.filter.RegulationFilter;
import com.aifishing.planning.filter.RejectionReason;
import com.aifishing.planning.filter.SafetyFilter;
import com.aifishing.planning.environment.GenerateOrientationCache;
import com.aifishing.planning.environment.TripClock;
import com.aifishing.planning.ranking.RankedCandidate;
import com.aifishing.planning.ranking.SpotRankingService;
import com.aifishing.planning.ranking.SpotScore;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.planning.repo.TripPlanRepository;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;
import com.aifishing.planning.repo.TripWaypointRepository;
import com.aifishing.planning.route.AccessPointSelector;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RequiredPointConstraintValidator;
import com.aifishing.planning.route.RoutePlanConstraints;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.TripLaunchResolver;
import com.aifishing.planning.spatial.GenerateProfiler;
import com.aifishing.planning.spatial.PendingZoneWaterPaths;
import com.aifishing.planning.spatial.SnapshotWaterPathService;
import com.aifishing.planning.spatial.SnapshotCandidateSelector;
import com.aifishing.planning.spatial.SpatialPlanningFacade;
import com.aifishing.planning.spatial.SpatialSnapshotService;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.TransitLegMaterializer;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.planning.tactics.TacticalRecommendation;
import com.aifishing.planning.tactics.TacticalRecommendationService;
import com.aifishing.planning.tactics.TacticsStatus;
import com.aifishing.planning.validation.TripPlanValidator;
import com.aifishing.strategy.domain.DataLimitation;
import com.aifishing.strategy.domain.DataLimitationCode;
import com.aifishing.strategy.domain.DepthRange;
import com.aifishing.strategy.domain.FishingStrategyProfile;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import com.aifishing.strategy.repo.StrategyRunRepository;
import com.aifishing.strategy.service.FishingStrategyService;
import com.aifishing.strategy.weather.WeatherAvailability;
import com.aifishing.strategy.weather.WeatherContext;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.webquota.service.WebPlanQuotaService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripPlanningService {

    private static final Logger log = LoggerFactory.getLogger(TripPlanningService.class);
    private static final Duration RUNNING_STALE = Duration.ofMinutes(15);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final CurrentUser currentUser;
    private final TripRepository tripRepository;
    private final LakeRepository lakeRepository;
    private final BoatRepository boatRepository;
    private final GearRepository gearRepository;
    private final StrategyRunRepository strategyRunRepository;
    private final FishingStrategyService fishingStrategyService;
    private final LakeFeatureRepository featureRepository;
    private final LakeBoundaryRecordRepository boundaryRepository;
    private final LakeWaterwayRepository waterwayRepository;
    private final FishingRestrictionRepository restrictionRepository;
    private final LakeDatasetStatusRepository datasetStatusRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TripWaypointRepository tripWaypointRepository;
    private final PlanningRunRepository planningRunRepository;
    private final PlanningRunPersistence persistence;
    private final AccessPointSelector accessPointSelector;
    private final TripLaunchResolver tripLaunchResolver;
    private final LakePlanningGeometryLoader geometryLoader;
    private final CandidateGenerator candidateGenerator;
    private final SpotRankingService rankingService;
    private final EmpiricalRankingProvider empiricalRankingProvider;
    private final RoutePlanner routePlanner;
    private final TripPlanValidator validator;
    private final PlanningProperties properties;
    private final ObjectMapper objectMapper;
    private final List<CandidateFilter> filters;
    private final List<CandidateFilter> launchIndependentFilters;
    private final List<CandidateFilter> launchDependentFilters;
    private final TripPlanAssembler assembler;
    private final BoatCapabilityResolver boatCapabilityResolver;
    private final EffectiveBoatCapabilityFactory effectiveBoatCapabilityFactory;
    private final StructurePipelineReadinessService readinessService;
    private final SpatialPlanningFacade spatialPlanningFacade;
    private final SpatialSnapshotService spatialSnapshotService;
    private final SnapshotCandidateSelector snapshotCandidateSelector;
    private final MacroCandidateShortlist macroCandidateShortlist;
    private final TacticalRecommendationService tacticalRecommendationService;
    private final TransitLegMaterializer transitLegMaterializer;
    private final SnapshotWaterPathService snapshotWaterPathService;
    private final ClientIdentity clientIdentity;
    private final WebPlanQuotaService webPlanQuotaService;
    private final TripPlanningInputSnapshotService inputSnapshotService;
    private final TripPlanningInputSnapshotRepository inputSnapshotRepository;
    private final PlanningCandidatePool planningCandidatePool;
    private final RequiredPointConstraintValidator requiredPointConstraintValidator;
    private final Executor planGenerateExecutor;

    public TripPlanningService(
            CurrentUser currentUser,
            TripRepository tripRepository,
            LakeRepository lakeRepository,
            BoatRepository boatRepository,
            GearRepository gearRepository,
            StrategyRunRepository strategyRunRepository,
            FishingStrategyService fishingStrategyService,
            LakeFeatureRepository featureRepository,
            LakeBoundaryRecordRepository boundaryRepository,
            LakeWaterwayRepository waterwayRepository,
            FishingRestrictionRepository restrictionRepository,
            LakeDatasetStatusRepository datasetStatusRepository,
            TripPlanRepository tripPlanRepository,
            TripWaypointRepository tripWaypointRepository,
            PlanningRunRepository planningRunRepository,
            PlanningRunPersistence persistence,
            AccessPointSelector accessPointSelector,
            TripLaunchResolver tripLaunchResolver,
            LakePlanningGeometryLoader geometryLoader,
            CandidateGenerator candidateGenerator,
            SpotRankingService rankingService,
            EmpiricalRankingProvider empiricalRankingProvider,
            RoutePlanner routePlanner,
            TripPlanValidator validator,
            PlanningProperties properties,
            ObjectMapper objectMapper,
            AccessibilityFilter accessibilityFilter,
            RegulationFilter regulationFilter,
            BoatCapabilityFilter boatCapabilityFilter,
            SafetyFilter safetyFilter,
            TripPlanAssembler assembler,
            BoatCapabilityResolver boatCapabilityResolver,
            EffectiveBoatCapabilityFactory effectiveBoatCapabilityFactory,
            StructurePipelineReadinessService readinessService,
            SpatialPlanningFacade spatialPlanningFacade,
            SpatialSnapshotService spatialSnapshotService,
            SnapshotCandidateSelector snapshotCandidateSelector,
            MacroCandidateShortlist macroCandidateShortlist,
            TacticalRecommendationService tacticalRecommendationService,
            TransitLegMaterializer transitLegMaterializer,
            SnapshotWaterPathService snapshotWaterPathService,
            ClientIdentity clientIdentity,
            WebPlanQuotaService webPlanQuotaService,
            TripPlanningInputSnapshotService inputSnapshotService,
            TripPlanningInputSnapshotRepository inputSnapshotRepository,
            PlanningCandidatePool planningCandidatePool,
            RequiredPointConstraintValidator requiredPointConstraintValidator,
            @Qualifier("planGenerateExecutor") Executor planGenerateExecutor
    ) {
        this.currentUser = currentUser;
        this.tripRepository = tripRepository;
        this.lakeRepository = lakeRepository;
        this.boatRepository = boatRepository;
        this.gearRepository = gearRepository;
        this.strategyRunRepository = strategyRunRepository;
        this.fishingStrategyService = fishingStrategyService;
        this.featureRepository = featureRepository;
        this.boundaryRepository = boundaryRepository;
        this.waterwayRepository = waterwayRepository;
        this.restrictionRepository = restrictionRepository;
        this.datasetStatusRepository = datasetStatusRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.tripWaypointRepository = tripWaypointRepository;
        this.planningRunRepository = planningRunRepository;
        this.persistence = persistence;
        this.accessPointSelector = accessPointSelector;
        this.tripLaunchResolver = tripLaunchResolver;
        this.geometryLoader = geometryLoader;
        this.candidateGenerator = candidateGenerator;
        this.rankingService = rankingService;
        this.empiricalRankingProvider = empiricalRankingProvider;
        this.routePlanner = routePlanner;
        this.validator = validator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.filters = List.of(accessibilityFilter, regulationFilter, boatCapabilityFilter, safetyFilter);
        this.launchIndependentFilters = List.of(accessibilityFilter, regulationFilter, safetyFilter);
        this.launchDependentFilters = List.of(boatCapabilityFilter);
        this.assembler = assembler;
        this.boatCapabilityResolver = boatCapabilityResolver;
        this.effectiveBoatCapabilityFactory = effectiveBoatCapabilityFactory;
        this.readinessService = readinessService;
        this.spatialPlanningFacade = spatialPlanningFacade;
        this.spatialSnapshotService = spatialSnapshotService;
        this.snapshotCandidateSelector = snapshotCandidateSelector;
        this.macroCandidateShortlist = macroCandidateShortlist;
        this.tacticalRecommendationService = tacticalRecommendationService;
        this.transitLegMaterializer = transitLegMaterializer;
        this.snapshotWaterPathService = snapshotWaterPathService;
        this.clientIdentity = clientIdentity;
        this.webPlanQuotaService = webPlanQuotaService;
        this.inputSnapshotService = inputSnapshotService;
        this.inputSnapshotRepository = inputSnapshotRepository;
        this.planningCandidatePool = planningCandidatePool;
        this.requiredPointConstraintValidator = requiredPointConstraintValidator;
        this.planGenerateExecutor = planGenerateExecutor;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GeneratePlanResponse generate(UUID tripId, GeneratePlanRequest request) {
        return generate(tripId, request, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GeneratePlanResponse generate(UUID tripId, GeneratePlanRequest request, String idempotencyKey) {
        GenerateAcceptance started = startGenerate(tripId, request, idempotencyKey);
        if (started.replay() != null) {
            return started.replay();
        }
        return executeGenerate(started);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<GeneratePlanResponse> generateMaybeAsync(
            UUID tripId,
            GeneratePlanRequest request,
            String idempotencyKey,
            boolean async
    ) {
        if (!async) {
            return ResponseEntity.ok(generate(tripId, request, idempotencyKey));
        }
        GenerateAcceptance started = startGenerate(tripId, request, idempotencyKey);
        if (started.replay() != null) {
            return ResponseEntity.ok(started.replay());
        }
        if (!started.resumeInProgress()) {
            GenerateProfiler.detach();
            planGenerateExecutor.execute(() -> {
                try {
                    executeGenerate(started);
                } catch (RuntimeException ex) {
                    log.warn("Async planning failed for trip {}: {}", started.tripId(), ex.getMessage());
                    markExecuteFailed(started, ex);
                } finally {
                    GenerateProfiler.clear();
                }
            });
        } else {
            GenerateProfiler.clear();
        }
        return ResponseEntity.accepted().body(assembler.toGenerateResponse(started.running(), null, List.of()));
    }

    private GenerateAcceptance startGenerate(UUID tripId, GeneratePlanRequest request, String idempotencyKey) {
        Trip trip = requireOwned(tripId);
        ClientChannel channel = clientIdentity.channel();
        UUID userId = trip.getUserId();
        String key = null;
        if (channel == ClientChannel.WEB) {
            key = webPlanQuotaService.requireIdempotencyKey(idempotencyKey);
            var complete = webPlanQuotaService.replayIfComplete(userId, key);
            if (complete.isPresent()) {
                return GenerateAcceptance.completed(complete.get());
            }
            var inProgress = webPlanQuotaService.replayIfInProgress(userId, key);
            if (inProgress.isPresent()) {
                PlanningRun running = planningRunRepository.findById(inProgress.get().planningRunId()).orElseThrow();
                return GenerateAcceptance.inProgress(running, tripId, request, userId, channel, key);
            }
            webPlanQuotaService.precheck(userId);
        }
        GenerateProfiler profiler = GenerateProfiler.begin();
        try {
            StrategyRun strategyRun = resolveStrategy(trip, request);
            PlanningRun running = persistence.insertRunning(trip.getId(), strategyRun, properties.getAlgorithmVersion(), channel);
            if (channel == ClientChannel.WEB) {
                try {
                    webPlanQuotaService.claimAttempt(userId, trip.getId(), key, running.getId());
                } catch (WebPlanQuotaService.CompletedIdempotentGeneration replay) {
                    GenerateProfiler.clear();
                    planningRunRepository.deleteById(running.getId());
                    return GenerateAcceptance.completed(webPlanQuotaService.requireReplay(replay));
                } catch (WebPlanQuotaService.InProgressIdempotentGeneration inProgress) {
                    GenerateProfiler.clear();
                    planningRunRepository.deleteById(running.getId());
                    PlanningRun existing = planningRunRepository.findById(inProgress.planningRunId()).orElseThrow();
                    return GenerateAcceptance.inProgress(existing, tripId, request, userId, channel, key);
                }
            }
            return GenerateAcceptance.started(running, tripId, request, userId, channel, key, profiler);
        } catch (RuntimeException ex) {
            GenerateProfiler.clear();
            throw ex;
        }
    }

    private GeneratePlanResponse executeGenerate(GenerateAcceptance started) {
        UUID tripId = started.tripId();
        GeneratePlanRequest request = started.request();
        ClientChannel channel = started.channel();
        String key = started.idempotencyKey();
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new NotFoundException("Trip not found"));
        PlanningRun running = planningRunRepository.findById(started.running().getId())
                .orElseThrow(() -> new NotFoundException("Planning run not found"));
        // Freeze live template + required points for this run. Later template edits do not change history.
        inputSnapshotService.freezeForRun(trip, running.getId());
        PlanningMode planningMode = PlanningMode.orAi(trip.getPlanningMode());
        if (running.getStrategyRunId() == null) {
            throw new NotFoundException("Strategy run not found");
        }
        StrategyRun strategyRun = strategyRunRepository.findById(running.getStrategyRunId())
                .orElseThrow(() -> new NotFoundException("Strategy run not found"));
        Map<String, Object> rankingConfig = rankingConfig();
        List<String> warnings = new ArrayList<>();
        Map<RejectionReason, Integer> rejections = new EnumMap<>(RejectionReason.class);
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        GenerateProfiler profiler = attachExecuteProfiler(started);
        UUID snapshotId = null;
        try {
            Lake lake = lakeRepository.findById(trip.getLakeId())
                    .orElseThrow(() -> new NotFoundException("Lake not found"));
            FishingStrategyProfile profile = parseProfile(strategyRun);
            if (strategyRun.getFeatureAnalysisVersion() == null || strategyRun.getFeatureAnalysisVersion().isBlank()) {
                return fail(running, inputSnapshot, rankingConfig, rejections, warnings,
                        "STALE_OR_MISSING_FEATURE_SNAPSHOT", trip.getUserId(), key);
            }
            long snapshotCount = featureRepository.countByLakeIdAndPipelineAndAnalysisVersion(
                    lake.getId(), strategyRun.getFeaturePipeline(), strategyRun.getFeatureAnalysisVersion());
            inputSnapshot.put("tripId", trip.getId().toString());
            inputSnapshot.put("lakeId", lake.getId().toString());
            inputSnapshot.put("strategyRunId", strategyRun.getId().toString());
            inputSnapshot.put("featurePipeline", strategyRun.getFeaturePipeline().name());
            inputSnapshot.put("featureAnalysisVersion", strategyRun.getFeatureAnalysisVersion());
            inputSnapshot.put("fishingMode", trip.getFishingMode().name());
            inputSnapshot.put("planningMode", planningMode.name());
            inputSnapshot.put("snapshotFeatureCount", snapshotCount);

            if (snapshotCount == 0) {
                String code = hasLimitation(profile, DataLimitationCode.STRUCTURE_NONE_AFTER_ANALYSIS)
                        ? "STRUCTURE_NONE_AFTER_ANALYSIS"
                        : "STALE_OR_MISSING_FEATURE_SNAPSHOT";
                return fail(running, inputSnapshot, rankingConfig, rejections, warnings, code, trip.getUserId(), key);
            }

            Boat boat = trip.getBoatId() == null ? null : boatRepository.findById(trip.getBoatId()).orElse(null);
            WeatherContext weather = parseWeather(strategyRun);
            addWeatherWarnings(weather, warnings);
            String regulationStatus = regulationStatus(lake.getId());
            addRegulationCoverageWarnings(regulationStatus, warnings);

            ResolvedBoatCapability baseline = null;
            EffectiveBoatCapability effective = null;
            if (trip.getFishingMode() == FishingMode.BOAT && boat != null) {
                baseline = boatCapabilityResolver.resolve(boat);
                effective = effectiveBoatCapabilityFactory.from(boat, baseline, weather, properties);
                for (String warning : baseline.warnings()) {
                    if (!warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                }
                inputSnapshot.put("boatCapability", boatCapabilitySnapshot(baseline, effective));
            }

            LakePlanningGeometry geometry = geometryLoader.load(lake);
            AccessResolution access = AccessResolution.unknown();
            ResolvedTripLaunch launch = null;
            if (trip.getFishingMode() == FishingMode.SHORE) {
                access = tripLaunchResolver.shoreAccess(trip, request == null ? null : request.accessPointId());
                launch = tripLaunchResolver.fromShore(access);
                snapshotAccess(inputSnapshot, access, launch);
                if (access.status() == AccessResolution.AccessStatus.UNKNOWN) {
                    warnings.add("ACCESS_UNKNOWN");
                }
            }

            PlanningContext context = new PlanningContext(
                    trip,
                    lake,
                    boat,
                    access,
                    geometry,
                    restrictionRepository.findByLakeId(lake.getId()),
                    regulationStatus,
                    weather,
                    gearTypes(trip.getUserId()),
                    profile,
                    strategyRun,
                    properties,
                    warnings,
                    baseline,
                    effective,
                    launch,
                    null,
                    new GenerateOrientationCache(),
                    new PendingZoneWaterPaths()
            );

            var ready = spatialSnapshotService.findReady(
                    lake.getId(), strategyRun.getFeaturePipeline(), strategyRun.getFeatureAnalysisVersion());
            if (ready.isEmpty()) {
                return fail(running, inputSnapshot, rankingConfig, rejections, warnings, "SPATIAL_SNAPSHOT_NOT_READY", trip.getUserId(), key);
            }
            profiler.start(GenerateProfiler.SPATIAL_SNAPSHOT_LOAD);
            SpatialSnapshotView view = spatialSnapshotService.load(ready.get().getId());
            profiler.end(GenerateProfiler.SPATIAL_SNAPSHOT_LOAD);
            snapshotId = view.id();
            profiler.set("snapshotTargetCount", view.targets().size());
            profiler.set("snapshotZoneCount", view.zones().size());
            context = context.withSnapshot(view);
            inputSnapshot.put("spatialPlanningSnapshotId", view.id().toString());
            inputSnapshot.put("spatialSnapshotCounts", view.snapshot().getCounts());

            SnapshotCandidateSelector.Result generated = snapshotCandidateSelector.select(view, context, rejections);
            if (generated.usedDepthFallback()) {
                warnings.add("DEPTH_FALLBACK");
            }
            List<CandidateSpot> aiSpots = macroCandidateShortlist.attachSnapshotMembership(
                    generated.spots(), context);
            PlanningCandidatePool.Result pool = planningCandidatePool.build(
                    planningMode, aiSpots, context, running.getId());
            for (String warning : pool.warnings()) {
                if (!warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
            List<CandidateSpot> requiredConstraints = pool.requiredConstraints();
            List<CandidateSpot> spatialSpots = pool.candidates();

            List<CandidateSpot> accepted;
            if (trip.getFishingMode() == FishingMode.BOAT) {
                List<CandidateSpot> independent = applyFilters(
                        spatialSpots, context, rejections, warnings, launchIndependentFilters);
                // Required Points are not in the optional pool; include them so AUTO launch
                // still has geography when the mode pool is template/required-only.
                List<CandidateSpot> launchCandidates = withRequiredForLaunch(independent, requiredConstraints);
                TripLaunchResolver.Result resolved = tripLaunchResolver.resolveBoat(
                        trip,
                        request == null ? null : request.accessPointId(),
                        geometry,
                        context,
                        launchCandidates
                );
                if (resolved.failed()) {
                    return fail(running, inputSnapshot, rankingConfig, rejections, warnings, resolved.errorCode(), trip.getUserId(), key);
                }
                launch = resolved.launch();
                access = tripLaunchResolver.toAccessResolution(launch);
                snapshotAccess(inputSnapshot, access, launch);
                context = context.withLaunch(access, launch);
                for (String warning : launch.warnings()) {
                    if (!warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                }
                accepted = applyFilters(independent, context, rejections, warnings, launchDependentFilters);
            } else {
                accepted = applyFilters(spatialSpots, context, rejections, warnings, filters);
            }
            try {
                requiredPointConstraintValidator.validate(requiredConstraints, context);
            } catch (RequiredPointConstraintValidator.RequiredPointInfeasibleException ex) {
                return fail(
                        running,
                        inputSnapshot,
                        rankingConfig,
                        rejections,
                        warnings,
                        ex.errorCode() + ":" + ex.pointReason(),
                        trip.getUserId(),
                        key);
            }
            if (accepted.isEmpty() && requiredConstraints.isEmpty()) {
                if (planningMode == PlanningMode.AI) {
                    accepted = nearestLaunchReachable(
                            generated.allTargets(),
                            context,
                            warnings,
                            properties.getCandidates().getMaxUnassignedAtomics());
                    accepted = macroCandidateShortlist.attachSnapshotMembership(accepted, context);
                    accepted = tagSource(accepted, CandidateSource.AI);
                }
                if (accepted.isEmpty()) {
                    return fail(running, inputSnapshot, rankingConfig, rejections, warnings, "NO_CANDIDATES", trip.getUserId(), key);
                }
                if (!warnings.contains("LAUNCH_PROXIMITY_FALLBACK")) {
                    warnings.add("LAUNCH_PROXIMITY_FALLBACK");
                }
            }
            accepted = macroCandidateShortlist.select(accepted, context);
            accepted = forceIncludeRequired(accepted, requiredConstraints);

            profiler.start(GenerateProfiler.DYNAMIC_SCORING);
            List<RankedCandidate> ranked = rank(accepted, context);
            profiler.end(GenerateProfiler.DYNAMIC_SCORING);
            ranked = spatialPlanningFacade.attachZones(ranked, context);
            RoutePlanConstraints routeConstraints = RoutePlanConstraints.of(requiredConstraints, planningMode);
            RoutePlanner.RouteResult route = routePlanner.plan(ranked, context, routeConstraints);
            if (warnings.contains("WEATHER_UNSAFE")) {
                flushZoneWaterPaths(context);
                return fail(running, inputSnapshot, rankingConfig, rejections, warnings, "WEATHER_UNSAFE", trip.getUserId(), key);
            }
            if (route.hardConstraintFailure() != null) {
                flushZoneWaterPaths(context);
                return fail(
                        running,
                        inputSnapshot,
                        rankingConfig,
                        rejections,
                        warnings,
                        route.hardConstraintFailure(),
                        trip.getUserId(),
                        key);
            }
            if (route.usedGlobalFallback()) {
                warnings.add("WINDOW_FALLBACK_GLOBAL");
            }
            List<PlannedStop> stops = route.stops();
            if (stops.isEmpty()) {
                if (planningMode == PlanningMode.AI && requiredConstraints.isEmpty()) {
                    List<CandidateSpot> nearer = nearestLaunchReachable(
                            generated.allTargets(), context, warnings, properties.getCandidates().getMaxUnassignedAtomics());
                    nearer = macroCandidateShortlist.attachSnapshotMembership(nearer, context);
                    nearer = tagSource(nearer, CandidateSource.AI);
                    if (!nearer.isEmpty()) {
                        accepted = macroCandidateShortlist.select(nearer, context);
                        profiler.start(GenerateProfiler.DYNAMIC_SCORING);
                        ranked = rank(accepted, context);
                        profiler.end(GenerateProfiler.DYNAMIC_SCORING);
                        ranked = spatialPlanningFacade.attachZones(ranked, context);
                        route = routePlanner.plan(ranked, context, routeConstraints);
                        stops = route.stops();
                        if (route.usedGlobalFallback() && !warnings.contains("WINDOW_FALLBACK_GLOBAL")) {
                            warnings.add("WINDOW_FALLBACK_GLOBAL");
                        }
                        if (!stops.isEmpty() && !warnings.contains("LAUNCH_PROXIMITY_FALLBACK")) {
                            warnings.add("LAUNCH_PROXIMITY_FALLBACK");
                        }
                    }
                }
                if (stops.isEmpty()) {
                    flushZoneWaterPaths(context);
                    String code = requiredConstraints.isEmpty()
                            ? "NO_CANDIDATES"
                            : RoutePlanConstraints.REQUIRED_SET_INFEASIBLE;
                    return fail(running, inputSnapshot, rankingConfig, rejections, warnings, code, trip.getUserId(), key);
                }
            }
            if (!RoutePlanConstraints.coversRequired(
                    stops,
                    routeConstraints.requiredOpportunityIds())) {
                flushZoneWaterPaths(context);
                return fail(
                        running,
                        inputSnapshot,
                        rankingConfig,
                        rejections,
                        warnings,
                        RoutePlanConstraints.REQUIRED_SET_INFEASIBLE,
                        trip.getUserId(),
                        key);
            }
            flushZoneWaterPaths(context);
            if (stops.size() <= 2) {
                warnings.add("SPARSE_CANDIDATES");
            }
            profiler.start(GenerateProfiler.PLAN_VALIDATION);
            String validation = validator.validate(stops, context, route);
            profiler.end(GenerateProfiler.PLAN_VALIDATION);
            if (validation != null) {
                return fail(running, inputSnapshot, rankingConfig, rejections, warnings, validation, trip.getUserId(), key);
            }

            boolean includeAiTactics = request != null && request.aiTacticsRequested();
            profiler.set("tacticsRequested", includeAiTactics ? 1 : 0);
            TacticalRecommendationService.TacticalPlan tactics;
            TacticsStatus tacticsStatus;
            if (includeAiTactics) {
                tactics = tacticalRecommendationService.recommend(stops, context);
                tacticsStatus = tactics.usable() ? TacticsStatus.READY : TacticsStatus.FAILED;
                if (tactics.warning() != null && !warnings.contains(tactics.warning())) {
                    warnings.add(tactics.warning());
                }
            } else {
                tactics = TacticalRecommendationService.TacticalPlan.empty();
                tacticsStatus = TacticsStatus.NONE;
            }
            profiler.tag("tacticsStatus", tacticsStatus.name());
            TripPlan plan = toPlan(trip, strategyRun, running.getId(), context, route, warnings, includeAiTactics, tacticsStatus);
            List<TripWaypoint> waypoints = toWaypoints(route.stops(), context, tactics.byVisitId());
            List<TripPlanTransitLeg> transitLegs = transitLegMaterializer.materialize(route, context);
            profiler.start(GenerateProfiler.PLAN_PERSIST);
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("candidateCount", generated.spots().size());
            usage.put("acceptedCount", accepted.size());
            usage.put("compressionSummary", profiler.compression().toMap());
            PlanningBalanceResponse balance = PlanningBalance.forRun(
                    running.getId(), waypoints, inputSnapshotRepository);
            usage.put("planningBalance", PlanningBalance.toUsageMap(balance));
            profiler.end(GenerateProfiler.PLAN_PERSIST);
            usage.put("profiler", finishAndLogProfile(profiler, running, lake.getId(),
                    strategyRun.getFeaturePipeline().name(), snapshotId));
            PlanningRun completed = persistence.complete(
                    running.getId(),
                    inputSnapshot,
                    rankingConfig,
                    filterSummary(rejections, generated.spots().size(), accepted.size(), stops.size()),
                    warnings,
                    usage,
                    plan,
                    waypoints,
                    transitLegs,
                    trip.getUserId(),
                    key
            );
            TripPlan saved = tripPlanRepository.findById(plan.getId()).orElse(plan);
            return assembler.toGenerateResponse(completed, saved, tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(saved.getId()));
        } catch (BadRequestException | NotFoundException ex) {
            if (GenerateProfiler.attached()) {
                finishAndLogProfile(profiler, running, trip.getLakeId(),
                        strategyRun.getFeaturePipeline() == null ? null : strategyRun.getFeaturePipeline().name(),
                        snapshotId);
            }
            if (channel == ClientChannel.WEB) {
                webPlanQuotaService.markFailed(trip.getUserId(), key, running.getId());
            }
            throw ex;
        } catch (Exception ex) {
            log.warn("Planning failed for trip {}: {}", tripId, ex.getMessage());
            return fail(running, inputSnapshot, rankingConfig, rejections, warnings, ex.getMessage(), trip.getUserId(), key);
        } finally {
            GenerateProfiler.clear();
        }
    }

    @Transactional
    public PlanningRunResponse ownedRun(UUID tripId, UUID runId) {
        requireOwned(tripId);
        PlanningRun run = requireRunOnTrip(tripId, runId);
        if (run.getStatus() == PlanningRunStatus.RUNNING
                && run.getStartedAt() != null
                && run.getStartedAt().isBefore(Instant.now().minus(RUNNING_STALE))) {
            run = persistence.fail(
                    run.getId(),
                    run.getInputSnapshot() == null ? Map.of() : run.getInputSnapshot(),
                    run.getRankingConfig() == null ? Map.of() : run.getRankingConfig(),
                    run.getFilterSummary() == null ? Map.of() : run.getFilterSummary(),
                    run.getWarnings() == null ? List.of() : run.getWarnings(),
                    run.getUsageMetadata() == null ? Map.of() : run.getUsageMetadata(),
                    "GENERATION_TIMEOUT"
            );
            webPlanQuotaService.markFailedByPlanningRun(run.getId());
        }
        return assembler.toRunResponse(run);
    }

    private void markExecuteFailed(GenerateAcceptance started, Exception ex) {
        try {
            PlanningRun running = planningRunRepository.findById(started.running().getId()).orElse(null);
            if (running == null || running.getStatus() != PlanningRunStatus.RUNNING) {
                return;
            }
            fail(
                    running,
                    Map.of(),
                    rankingConfig(),
                    new EnumMap<>(RejectionReason.class),
                    List.of(),
                    ex.getMessage() == null ? "GENERATION_FAILED" : ex.getMessage(),
                    started.userId(),
                    started.idempotencyKey()
            );
        } catch (Exception persistEx) {
            log.warn("Could not persist async planning failure for trip {}", started.tripId(), persistEx);
        }
    }

    private PlanningRun requireRunOnTrip(UUID tripId, UUID runId) {
        PlanningRun run = planningRunRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Planning run not found"));
        if (!tripId.equals(run.getTripId())) {
            throw new NotFoundException("Planning run not found");
        }
        return run;
    }

    private record GenerateAcceptance(
            GeneratePlanResponse replay,
            PlanningRun running,
            UUID tripId,
            GeneratePlanRequest request,
            UUID userId,
            ClientChannel channel,
            String idempotencyKey,
            boolean resumeInProgress,
            GenerateProfiler profiler
    ) {
        static GenerateAcceptance completed(GeneratePlanResponse replay) {
            return new GenerateAcceptance(replay, null, null, null, null, null, null, false, null);
        }

        static GenerateAcceptance started(
                PlanningRun running,
                UUID tripId,
                GeneratePlanRequest request,
                UUID userId,
                ClientChannel channel,
                String idempotencyKey,
                GenerateProfiler profiler
        ) {
            return new GenerateAcceptance(null, running, tripId, request, userId, channel, idempotencyKey, false, profiler);
        }

        static GenerateAcceptance inProgress(
                PlanningRun running,
                UUID tripId,
                GeneratePlanRequest request,
                UUID userId,
                ClientChannel channel,
                String idempotencyKey
        ) {
            return new GenerateAcceptance(null, running, tripId, request, userId, channel, idempotencyKey, true, null);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diagnostics(UUID tripId) {
        requireOwned(tripId);
        TripPlan plan = tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(
                        tripId, List.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED))
                .orElseThrow(() -> new NotFoundException("No plan for trip"));
        List<TripWaypoint> waypoints = tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planId", plan.getId().toString());
        out.put("algorithmVersion", plan.getPlanningAlgorithmVersion());
        List<Map<String, Object>> stops = new ArrayList<>();
        for (TripWaypoint waypoint : waypoints) {
            Map<String, Object> stop = new LinkedHashMap<>();
            stop.put("sequence", waypoint.getSequence());
            stop.put("visitKind", waypoint.getVisitKind() == null ? waypoint.resolvedTargetKind().name() : waypoint.getVisitKind().name());
            stop.put("targetKind", waypoint.resolvedTargetKind().name());
            stop.put("sourceFeatureId", waypoint.getLakeFeatureId());
            stop.put("fishingTargetId", waypoint.getFishingTargetId());
            stop.put("zoneId", waypoint.getZoneId());
            stop.put("visitScopeId", waypoint.getVisitScopeId());
            stop.put("visitScopeMemberIds", waypoint.getVisitScopeMemberIds());
            stop.put("why", waypoint.getReason());
            stop.put("entry", waypoint.resolvedEntryPoint() == null ? null : List.of(waypoint.resolvedEntryPoint().getX(), waypoint.resolvedEntryPoint().getY()));
            stop.put("exit", waypoint.resolvedExitPoint() == null ? null : List.of(waypoint.resolvedExitPoint().getX(), waypoint.resolvedExitPoint().getY()));
            stop.put("plannedFishingMinutes", waypoint.getPlannedFishingMinutes());
            stop.put("plannedInternalTransitMinutes", waypoint.getPlannedInternalTransitMinutes());
            stop.put("plannedVisitMinutes", waypoint.resolvedVisitMinutes());
            stop.put("traversalKey", waypoint.getTraversalKey());
            stop.put("closedLoop", waypoint.getClosedLoop());
            stop.put("splitReason", waypoint.getMetadata() == null ? null : waypoint.getMetadata().get("splitReason"));
            stops.add(stop);
        }
        out.put("stops", stops);
        return out;
    }

    @Transactional(readOnly = true)
    public TripPlanResponse current(UUID tripId) {
        requireOwned(tripId);
        TripPlan plan = tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(
                        tripId, List.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED))
                .orElseThrow(() -> new NotFoundException("No plan for trip"));
        return assembler.toPlanResponse(plan, tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId()));
    }

    @Transactional(readOnly = true)
    public List<TripPlanResponse> history(UUID tripId) {
        requireOwned(tripId);
        return tripPlanRepository.findByTripIdOrderByVersionDesc(tripId).stream()
                .map(plan -> assembler.toPlanResponse(plan, tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripPlanMapDataResponse mapData(UUID tripId) {
        requireOwned(tripId);
        TripPlan plan = tripPlanRepository.findFirstByTripIdAndStatusInOrderByVersionDesc(
                        tripId, List.of(TripPlanStatus.GENERATED, TripPlanStatus.ACCEPTED))
                .orElseThrow(() -> new NotFoundException("No plan for trip"));
        return assembler.toMapData(plan, tripWaypointRepository.findByTripPlanIdOrderBySequenceAsc(plan.getId()));
    }

    @Transactional(readOnly = true)
    public List<PlanningRunSummaryResponse> runs(UUID tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new NotFoundException("Trip not found");
        }
        return planningRunRepository.findByTripIdOrderByStartedAtDesc(tripId).stream()
                .map(assembler::toRunSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanningRunResponse run(UUID tripId, UUID runId) {
        if (!tripRepository.existsById(tripId)) {
            throw new NotFoundException("Trip not found");
        }
        PlanningRun run = planningRunRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Planning run not found"));
        if (!tripId.equals(run.getTripId())) {
            throw new NotFoundException("Planning run not found");
        }
        return assembler.toRunResponse(run);
    }

    private StrategyRun resolveStrategy(Trip trip, GeneratePlanRequest request) {
        UUID strategyRunId = request == null ? null : request.strategyRunId();
        Pipeline requestedPipeline = request == null ? null : request.featurePipeline();
        if (strategyRunId != null && requestedPipeline != null) {
            throw new BadRequestException(
                    "GENERATE_PLAN_REQUEST_CONFLICT",
                    "strategyRunId and featurePipeline cannot both be supplied");
        }
        if (requestedPipeline == Pipeline.VISION) {
            throw new BadRequestException(
                    "FEATURE_PIPELINE_UNSUPPORTED",
                    "VISION is not a user-selectable Generate Plan pipeline");
        }
        StrategyRun run;
        if (strategyRunId != null) {
            run = strategyRunRepository.findById(strategyRunId)
                    .orElseThrow(() -> new NotFoundException("Strategy run not found"));
            if (!trip.getId().equals(run.getTripId())) {
                throw new BadRequestException("Strategy run does not belong to this trip");
            }
        } else {
            Pipeline pipeline = requestedPipeline == null
                    ? properties.getUserDefaultFeaturePipeline()
                    : requestedPipeline;
            if (pipeline == Pipeline.VISION || (pipeline != Pipeline.GIS && pipeline != Pipeline.HYBRID)) {
                throw new BadRequestException(
                        "FEATURE_PIPELINE_UNSUPPORTED",
                        pipeline + " is not a user-selectable Generate Plan pipeline");
            }
            StructurePipelineReadiness readiness = readinessService.evaluate(trip.getLakeId(), pipeline);
            if (!readiness.available()) {
                throw new BadRequestException(readiness.generatePlanErrorCode(), readiness.generatePlanErrorMessage());
            }
            run = fishingStrategyService.generate(trip.getId(), pipeline);
        }
        if (run == null || run.getStatus() != StrategyRunStatus.COMPLETED || run.getStrategyProfile() == null) {
            String detail = run == null || run.getErrorMessage() == null || run.getErrorMessage().isBlank()
                    ? "Trip has no COMPLETED strategy"
                    : run.getErrorMessage();
            throw new BadRequestException(detail);
        }
        return run;
    }

    private List<CandidateSpot> applyFilters(
            List<CandidateSpot> spots,
            PlanningContext context,
            Map<RejectionReason, Integer> rejections,
            List<String> warnings
    ) {
        return applyFilters(spots, context, rejections, warnings, filters);
    }

    private static List<CandidateSpot> withRequiredForLaunch(
            List<CandidateSpot> independent,
            List<CandidateSpot> required
    ) {
        if (required == null || required.isEmpty()) {
            return independent == null ? List.of() : independent;
        }
        if (independent == null || independent.isEmpty()) {
            return List.copyOf(required);
        }
        List<CandidateSpot> merged = new ArrayList<>(independent.size() + required.size());
        merged.addAll(independent);
        merged.addAll(required);
        return merged;
    }

    private static List<CandidateSpot> forceIncludeRequired(
            List<CandidateSpot> accepted,
            List<CandidateSpot> required
    ) {
        if (required == null || required.isEmpty()) {
            return accepted == null ? List.of() : accepted;
        }
        java.util.LinkedHashMap<UUID, CandidateSpot> byId = new java.util.LinkedHashMap<>();
        if (accepted != null) {
            for (CandidateSpot spot : accepted) {
                UUID id = spot.planningIdentity();
                if (id != null) {
                    byId.putIfAbsent(id, spot);
                } else {
                    byId.put(UUID.randomUUID(), spot);
                }
            }
        }
        for (CandidateSpot spot : required) {
            UUID id = spot.planningIdentity();
            if (id == null) {
                byId.put(UUID.randomUUID(), spot);
            } else {
                byId.putIfAbsent(id, spot);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static List<CandidateSpot> tagSource(List<CandidateSpot> spots, CandidateSource source) {
        if (spots == null || spots.isEmpty()) {
            return List.of();
        }
        List<CandidateSpot> out = new ArrayList<>(spots.size());
        for (CandidateSpot spot : spots) {
            CandidateSpot copy = spot.copy();
            copy.setCandidateSource(source);
            out.add(copy);
        }
        return out;
    }

    private List<CandidateSpot> applyFilters(
            List<CandidateSpot> spots,
            PlanningContext context,
            Map<RejectionReason, Integer> rejections,
            List<String> warnings,
            List<CandidateFilter> activeFilters
    ) {
        List<CandidateSpot> accepted = new ArrayList<>();
        for (CandidateSpot spot : spots) {
            boolean ok = true;
            for (CandidateFilter filter : activeFilters) {
                FilterResult result = filter.apply(spot, context);
                if (result.warning() != null && !warnings.contains(result.warning())) {
                    warnings.add(result.warning());
                }
                if (!result.accepted()) {
                    rejections.merge(result.reason(), 1, Integer::sum);
                    ok = false;
                    break;
                }
            }
            if (ok) {
                accepted.add(spot);
            }
        }
        return accepted;
    }

    private List<CandidateSpot> nearestLaunchReachable(
            List<CandidateSpot> pool,
            PlanningContext context,
            List<String> warnings,
            int limit
    ) {
        if (context.routeStartPoint() == null || pool == null || pool.isEmpty() || limit <= 0) {
            return List.of();
        }
        org.locationtech.jts.geom.Point start = context.routeStartPoint();
        List<CandidateSpot> nearest = pool.stream()
                .filter(spot -> spot.getLocation() != null)
                .sorted(Comparator.comparingDouble(spot -> GeoMetrics.distanceM(start, spot.getLocation())))
                .limit(Math.max(limit * 8L, 48L))
                .toList();
        List<CandidateFilter> all = new ArrayList<>();
        all.addAll(launchIndependentFilters);
        all.addAll(launchDependentFilters);
        Map<RejectionReason, Integer> local = new EnumMap<>(RejectionReason.class);
        List<CandidateSpot> ok = applyFilters(nearest, context, local, warnings, all);
        return ok.size() <= limit ? ok : new ArrayList<>(ok.subList(0, limit));
    }

    private List<RankedCandidate> rank(List<CandidateSpot> spots, PlanningContext context) {
        Map<UUID, EmpiricalEvidence> empirical = empiricalRankingProvider.scoreCandidates(
                context.lake().getId(),
                context.primarySpecies(),
                context.trip().getUserId(),
                spots
        );
        List<RankedCandidate> ranked = new ArrayList<>();
        Instant start = TripClock.startAt(context);
        for (CandidateSpot spot : spots) {
            DepthRange depth = windowDepth(context.profile(), spot);
            EmpiricalEvidence evidence = empirical.getOrDefault(
                    spot.getZoneId() != null ? spot.getZoneId() : spot.getFeatureId(),
                    EmpiricalEvidence.none());
            if (spot.getTechniques() == null || spot.getTechniques().isEmpty()) {
                var arrival = new com.aifishing.planning.ranking.ArrivalStrategyEvaluator()
                        .evaluate(spot, start, context);
                spot.setTechniques(arrival.techniques());
            }
            SpotScore score = rankingService.score(spot, context, depth, evidence);
            ranked.add(new RankedCandidate(spot, score, depth));
        }
        ranked.sort((a, b) -> Double.compare(b.score().finalScore(), a.score().finalScore()));
        return ranked;
    }

    private DepthRange windowDepth(FishingStrategyProfile profile, CandidateSpot spot) {
        if (profile == null) {
            return null;
        }
        var matching = com.aifishing.planning.ranking.ArrivalStrategyEvaluator.matchingWindow(
                profile, spot.getWindowFrom() != null ? spot.getWindowFrom() : null);
        if (matching != null) {
            return matching.preferredDepthM();
        }
        if (!profile.timeWindows().isEmpty()) {
            return profile.timeWindows().get(0).preferredDepthM();
        }
        return null;
    }

    private TripPlan toPlan(
            Trip trip,
            StrategyRun strategyRun,
            UUID planningRunId,
            PlanningContext context,
            com.aifishing.planning.route.            RoutePlanner.RouteResult route,
            List<String> warnings,
            boolean tacticsRequested,
            TacticsStatus tacticsStatus
    ) {
        List<PlannedStop> stops = route.stops();
        double meanFeature = stops.stream()
                .map(stop -> stop.candidate().spot().getFeatureConfidence())
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);
        double system = context.profile().systemConfidence() == null ? 0.5 : context.profile().systemConfidence();
        double planConfidence = clamp(0.5 * system + 0.5 * meanFeature, 0.05, 0.95);
        double travelM = stops.stream()
                .map(PlannedStop::fromPrevious)
                .filter(estimate -> estimate != null && !estimate.unknownTravel())
                .mapToDouble(com.aifishing.planning.route.TravelEstimate::distanceM)
                .sum();
        if (route.returnTravel() != null && !route.returnTravel().unknownTravel()) {
            travelM += route.returnTravel().distanceM();
        }
        double travelMin = route.totalTravelMinutes();
        Instant launchAt = route.plannedLaunchDepartureAt() == null ? TripClock.startAt(context) : route.plannedLaunchDepartureAt();
        Instant returnAt = route.plannedReturnAt() == null ? TripClock.endAt(context) : route.plannedReturnAt();
        double plannedMinutes = Math.max(0, Duration.between(launchAt, returnAt).toMinutes());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("accessStatus", context.access().status().name());
        metadata.put("fishingMode", trip.getFishingMode().name());
        if (context.access().accessPointId() != null) {
            metadata.put("accessPointId", context.access().accessPointId().toString());
        }
        if (context.access().name() != null) {
            metadata.put("accessPointName", context.access().name());
        }
        putLaunchMetadata(metadata, context.launch());
        if (context.baselineBoatCapability() != null) {
            metadata.put("boatCapability", boatCapabilitySnapshot(
                    context.baselineBoatCapability(),
                    context.effectiveBoatCapability()
            ));
        }
        metadata.put("scheduleAlgorithmVersion", properties.getAlgorithmVersion());
        metadata.put("tacticalAlgorithmVersion", TacticalRecommendationService.ALGORITHM_VERSION);
        Map<String, Object> scoreSnapshot = new LinkedHashMap<>();
        scoreSnapshot.put("meanFeatureConfidence", meanFeature);
        scoreSnapshot.put("systemConfidence", system);
        scoreSnapshot.put("waypointScores", stops.stream()
                .map(stop -> Map.of(
                        "featureId", stop.candidate().spot().getFeatureId().toString(),
                        "score", stop.candidate().score().finalScore(),
                        "timeAdjustedUtility", stop.timeScore() == null || stop.timeScore().finalTimeAdjustedUtility() == null
                                ? stop.candidate().score().finalScore()
                                : stop.timeScore().finalTimeAdjustedUtility()
                ))
                .toList());

        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(tripPlanRepository.maxVersion(trip.getId()) + 1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.now());
        plan.setStrategyRunId(strategyRun.getId());
        plan.setPlanningRunId(planningRunId);
        plan.setFeaturePipeline(strategyRun.getFeaturePipeline());
        plan.setFeatureAnalysisVersion(strategyRun.getFeatureAnalysisVersion());
        plan.setPlanningAlgorithmVersion(properties.getAlgorithmVersion());
        plan.setScheduleAlgorithmVersion(properties.getAlgorithmVersion());
        plan.setOverallPlanConfidence(BigDecimal.valueOf(planConfidence).setScale(4, RoundingMode.HALF_UP));
        plan.setTotalEstimatedTravelDistanceM(BigDecimal.valueOf(travelM).setScale(2, RoundingMode.HALF_UP));
        plan.setTotalEstimatedTravelMinutes(BigDecimal.valueOf(travelMin).setScale(2, RoundingMode.HALF_UP));
        plan.setPlannedLaunchDepartureAt(launchAt);
        plan.setPlannedReturnAt(returnAt);
        plan.setTotalFishingMinutes(BigDecimal.valueOf(route.totalFishingMinutes()).setScale(2, RoundingMode.HALF_UP));
        plan.setTotalPlannedMinutes(BigDecimal.valueOf(plannedMinutes).setScale(2, RoundingMode.HALF_UP));
        plan.setScheduleReserveMinutes(route.scheduleReserveMinutes());
        plan.setTotalWaitMinutes(route.totalWaitMinutes());
        if (route.waitEvents() != null && !route.waitEvents().isEmpty()) {
            plan.setScheduleEvents(route.waitEvents().stream().map(event -> event.toMap()).toList());
        }
        plan.setWarnings(List.copyOf(warnings));
        plan.setScoreSnapshot(scoreSnapshot);
        plan.setMetadata(metadata);
        plan.setTacticsRequested(tacticsRequested);
        plan.setTacticsStatus(tacticsStatus);
        return plan;
    }

    private List<TripWaypoint> toWaypoints(
            List<PlannedStop> stops,
            PlanningContext context,
            Map<UUID, TacticalRecommendation> tacticsByVisit
    ) {
        List<TripWaypoint> waypoints = new ArrayList<>();
        int sequence = 1;
        ZoneId zone = TripClock.zoneId(context);
        for (PlannedStop stop : stops) {
            CandidateSpot spot = stop.candidate().spot();
            com.aifishing.planning.ranking.SpotScore score = stop.candidate().score();
            TripWaypoint waypoint = new TripWaypoint();
            waypoint.setSequence(sequence++);
            waypoint.setLocation(spot.getLocation());
            waypoint.setCandidateSource(spot.getCandidateSource());
            waypoint.setTargetKind(spot.getTargetKind());
            waypoint.setVisitKind(spot.getTargetKind());
            waypoint.setVisitScopeId(spot.getVisitScopeId());
            waypoint.setVisitScopeMemberIds(spot.getZoneMembers().stream()
                    .map(CandidateSpot::getFishingTargetId)
                    .filter(java.util.Objects::nonNull)
                    .map(UUID::toString)
                    .toList());
            waypoint.setVisitEnvelope(spot.getVisitEnvelope());
            waypoint.setClosedLoop(spot.isClosedLoop());
            waypoint.setTraversalKey(stop.visitOption() == null || stop.visitOption().traversal() == null
                    ? spot.getTraversal().name()
                    : stop.visitOption().traversal().name());
            if (context.spatialSnapshot() != null) {
                waypoint.setSpatialPlanningSnapshotId(context.spatialSnapshot().id());
            }
            waypoint.setTargetGeometry(spot.getTargetGeometry());
            waypoint.setFishingCorridor(spot.getFishingCorridor());
            waypoint.setEntryPoint(stop.entryPoint());
            waypoint.setExitPoint(stop.exitPoint());
            if (spot.getSelectedFishingPath() instanceof org.locationtech.jts.geom.LineString path) {
                waypoint.setSelectedFishingPath(path);
            }
            if (spot.getFishingCorridorWidthM() != null) {
                waypoint.setFishingCorridorWidthM(BigDecimal.valueOf(spot.getFishingCorridorWidthM()));
            }
            waypoint.setZoneId(spot.getZoneId());
            waypoint.setFishingTargetId(spot.getFishingTargetId());
            waypoint.setPlannedVisitMinutes(stop.stayMinutes());
            waypoint.setPlannedFishingMinutes(stop.plannedFishingMinutes());
            waypoint.setPlannedInternalTransitMinutes(stop.plannedInternalTransitMinutes());
            waypoint.setPlannedWaitMinutes(stop.plannedWaitMinutes());
            waypoint.setPendingSubPlan(stop.zoneSubPlan());
            waypoint.setPlannedArrivalAt(stop.arrivalAt());
            waypoint.setPlannedDepartureAt(stop.departureAt());
            waypoint.setPlannedDwellMinutes(stop.stayMinutes());
            if (stop.arrivalAt() != null) {
                waypoint.setPlannedArrivalTime(stop.arrivalAt().atZone(zone).toLocalTime());
            }
            if (stop.departureAt() != null) {
                waypoint.setPlannedDepartureTime(stop.departureAt().atZone(zone).toLocalTime());
            }
            waypoint.setFeatureType(spot.getType());
            waypoint.setMinDepthM(decimal(spot.getMinDepthM()));
            waypoint.setMaxDepthM(decimal(spot.getMaxDepthM()));
            if (spot.getTargetKind() == com.aifishing.planning.spatial.TargetKind.ZONE
                    && !spot.getZoneMembers().isEmpty()) {
                waypoint.setLakeFeatureId(spot.getZoneMembers().get(0).getFeatureId());
            } else {
                waypoint.setLakeFeatureId(spot.getFeatureId());
            }
            waypoint.setRepresentativeDepthM(decimal(spot.getRepresentativeDepthM()));
            double persistedScore = stop.timeScore() != null && stop.timeScore().finalTimeAdjustedUtility() != null
                    ? stop.timeScore().finalTimeAdjustedUtility()
                    : score.finalScore();
            waypoint.setCandidateScore(BigDecimal.valueOf(clamp(persistedScore, 0, 1.5)).setScale(4, RoundingMode.HALF_UP));
            waypoint.setScoreBreakdown(objectMapper.convertValue(
                    stop.timeScore() == null ? score.breakdown() : stop.timeScore(), MAP));
            waypoint.setRecommendedTechniques(spot.techniqueTypes().stream().map(Enum::name).toList());
            if (waypoint.getRecommendedTechniques().isEmpty() && !spot.getZoneMembers().isEmpty()) {
                waypoint.setRecommendedTechniques(spot.getZoneMembers().get(0).techniqueTypes().stream().map(Enum::name).toList());
            }
            if (!waypoint.getRecommendedTechniques().isEmpty()) {
                waypoint.setRecommendedTechnique(waypoint.getRecommendedTechniques().get(0));
            }
            waypoint.setReason(explanation(spot));
            waypoint.setWhyThisTime(stop.whyThisTime() == null ? List.of() : List.copyOf(stop.whyThisTime()));
            waypoint.setEnvironment(stop.environment());
            if (!stop.fromPrevious().unknownTravel()) {
                waypoint.setEstimatedTravelDistanceFromPreviousM(
                        BigDecimal.valueOf(stop.fromPrevious().distanceM()).setScale(2, RoundingMode.HALF_UP));
                waypoint.setEstimatedTravelMinutesFromPrevious(
                        BigDecimal.valueOf(stop.fromPrevious().minutes()).setScale(2, RoundingMode.HALF_UP));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            UUID visitId = stop.visitId();
            if (visitId != null) {
                metadata.put("visitId", visitId.toString());
            }
            metadata.put("targetKind", spot.getTargetKind().name());
            metadata.put("visitKind", spot.getTargetKind().name());
            TripWaypointPlanMetadata.put(metadata, stop);
            if (spot.getVisitScopeId() != null) {
                metadata.put("visitScopeId", spot.getVisitScopeId().toString());
            }
            if (spot.isClosedLoop()) {
                metadata.put("closedLoop", true);
            }
            if (spot.getZoneId() != null) {
                metadata.put("zoneId", spot.getZoneId().toString());
            }
            metadata.put("plannedFishingMinutes", stop.plannedFishingMinutes());
            metadata.put("plannedInternalTransitMinutes", stop.plannedInternalTransitMinutes());
            metadata.put("analysisVersion", spot.getAnalysisVersion());
            metadata.put("landCrossingDetected", stop.fromPrevious().landCrossingDetected());
            metadata.put("appliedDetourFactor", stop.fromPrevious().appliedDetourFactor());
            if (stop.fromPrevious().unknownTravel()) {
                metadata.put("travelFromPreviousUnknown", true);
            }
            if (stop.precedingWaitMinutes() > 0) {
                metadata.put("precedingWaitMinutes", stop.precedingWaitMinutes());
                metadata.put("precedingWaitLocation", stop.precedingWaitLocation());
            }
            if (spot.isSecondaryTargetRestricted()) {
                metadata.put("secondaryTargetRestricted", true);
            }
            if (spot.isShoreAccessUnverified()) {
                metadata.put("shoreAccessUnverified", true);
            }
            if (!spot.getWarnings().isEmpty()) {
                metadata.put("warnings", List.copyOf(spot.getWarnings()));
            }
            waypoint.setMetadata(metadata);
            Map<UUID, TacticalRecommendation> tactics = tacticsByVisit == null ? Map.of() : tacticsByVisit;
            TacticalRecommendation tactical = visitId == null ? null : tactics.get(visitId);
            if (tactical == null && spot.getTargetKind() == com.aifishing.planning.spatial.TargetKind.ZONE) {
                if (stop.zoneSubPlan() != null && stop.zoneSubPlan().stops() != null) {
                    for (var micro : stop.zoneSubPlan().stops()) {
                        if (micro.spot() == null || micro.spot().planningIdentity() == null) {
                            continue;
                        }
                        tactical = tactics.get(micro.spot().planningIdentity());
                        if (tactical != null) {
                            break;
                        }
                    }
                }
                if (tactical == null) {
                    for (CandidateSpot member : spot.getZoneMembers()) {
                        UUID memberId = member.planningIdentity();
                        if (memberId != null) {
                            tactical = tactics.get(memberId);
                            if (tactical != null) {
                                break;
                            }
                        }
                    }
                }
            }
            if (tactical != null) {
                waypoint.setTactical(objectMapper.convertValue(tactical, MAP));
            }
            if (stop.zoneSubPlan() != null && stop.zoneSubPlan().stops() != null) {
                Map<UUID, Map<String, Object>> childTactical = new LinkedHashMap<>();
                for (var micro : stop.zoneSubPlan().stops()) {
                    if (micro.spot() == null || micro.spot().planningIdentity() == null) {
                        continue;
                    }
                    TacticalRecommendation child = tactics.get(micro.spot().planningIdentity());
                    if (child != null) {
                        childTactical.put(micro.spot().planningIdentity(), objectMapper.convertValue(child, MAP));
                    }
                }
                waypoint.setPendingChildTactical(childTactical);
            }
            waypoints.add(waypoint);
        }
        return waypoints;
    }

    private String explanation(CandidateSpot spot) {
        return SpotReason.format(
                spot.getType() == null ? null : spot.getType().name(),
                spot.getRepresentativeDepthM(),
                spot.getFeatureConfidence(),
                spot.getWindowFrom(),
                spot.getWindowTo(),
                spot.getStrategyRationale()
        );
    }

    private LakePlanningGeometry loadGeometry(Lake lake) {
        return geometryLoader.load(lake);
    }

    private void snapshotAccess(Map<String, Object> inputSnapshot, AccessResolution access, ResolvedTripLaunch launch) {
        inputSnapshot.put("accessStatus", access.status().name());
        if (access.accessPointId() != null) {
            inputSnapshot.put("accessPointId", access.accessPointId().toString());
        }
        putLaunchMetadata(inputSnapshot, launch);
    }

    private void putLaunchMetadata(Map<String, Object> target, ResolvedTripLaunch launch) {
        if (launch == null) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (launch.selectionMode() != null) {
            snapshot.put("mode", launch.selectionMode().name());
        }
        if (launch.officialAccessPointId() != null) {
            snapshot.put("officialAccessPointId", launch.officialAccessPointId().toString());
        }
        if (launch.officialName() != null) {
            snapshot.put("officialName", launch.officialName());
        }
        if (launch.officialSource() != null) {
            snapshot.put("officialSource", launch.officialSource());
        }
        if (launch.verification() != null) {
            snapshot.put("verification", launch.verification().name());
        }
        if (launch.accessType() != null) {
            snapshot.put("accessType", launch.accessType());
        }
        if (launch.customAccessType() != null) {
            snapshot.put("customAccessType", launch.customAccessType().name());
        }
        if (launch.shorelineKind() != null) {
            snapshot.put("shorelineKind", launch.shorelineKind().name());
        }
        if (launch.snapDistanceMeters() != null) {
            snapshot.put("snapDistanceM", launch.snapDistanceMeters());
        }
        if (launch.resolutionVersion() != null) {
            snapshot.put("resolutionVersion", launch.resolutionVersion());
        }
        if (launch.warnings() != null && !launch.warnings().isEmpty()) {
            snapshot.put("warnings", launch.warnings());
        }
        if (launch.autoScore() != null) {
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("coverage", launch.autoScore().coverage());
            score.put("travelCost", launch.autoScore().travelCost());
            score.put("boatFeasibility", launch.autoScore().boatFeasibility());
            score.put("accessConfidence", launch.autoScore().accessConfidence());
            score.put("overall", launch.autoScore().overall());
            score.put("algorithmVersion", launch.autoScore().algorithmVersion());
            snapshot.put("autoScore", score);
        }
        if (launch.displayPoint() != null) {
            snapshot.put("requestedPoint", Map.of(
                    "lat", launch.displayPoint().getY(),
                    "lng", launch.displayPoint().getX()
            ));
        }
        if (launch.shoreAccessPoint() != null) {
            snapshot.put("shoreAccessPoint", Map.of(
                    "lat", launch.shoreAccessPoint().getY(),
                    "lng", launch.shoreAccessPoint().getX()
            ));
        }
        if (launch.routeStartPoint() != null) {
            snapshot.put("routeStartPoint", Map.of(
                    "lat", launch.routeStartPoint().getY(),
                    "lng", launch.routeStartPoint().getX()
            ));
            snapshot.put("hasRouteStartPoint", true);
        }
        target.put("launchSelection", snapshot);
    }

    private List<GearType> gearTypes(UUID userId) {
        return gearRepository.findByUserIdOrderByNameAsc(userId).stream()
                .filter(gear -> gear.isActive())
                .map(gear -> gear.getType())
                .filter(type -> type != GearType.LURE)
                .toList();
    }

    private String regulationStatus(UUID lakeId) {
        return datasetStatusRepository.findByLakeIdOrderByDatasetTypeAsc(lakeId).stream()
                .filter(status -> status.getDatasetType() == DatasetType.REGULATION)
                .map(LakeDatasetStatus::getStatus)
                .map(Enum::name)
                .findFirst()
                .orElse(null);
    }

    private void addRegulationCoverageWarnings(String status, List<String> warnings) {
        if ("PARTIAL".equals(status)) {
            warnings.add("REGULATION_COVERAGE_PARTIAL");
        } else if ("NOT_AVAILABLE".equals(status) || DatasetStatusCode.NOT_AVAILABLE.name().equals(status)) {
            warnings.add("REGULATION_COVERAGE_UNAVAILABLE");
        }
    }

    private void addWeatherWarnings(WeatherContext weather, List<String> warnings) {
        if (weather == null || weather.availability() == null) {
            warnings.add("WEATHER_UNAVAILABLE");
            return;
        }
        if (weather.availability() == WeatherAvailability.UNAVAILABLE) {
            warnings.add("WEATHER_UNAVAILABLE");
        } else if (weather.availability() == WeatherAvailability.FAILED) {
            warnings.add("WEATHER_FAILED");
        } else if (weather.availability() == WeatherAvailability.OUT_OF_FORECAST_RANGE) {
            warnings.add("WEATHER_OUT_OF_FORECAST_RANGE");
        }
    }

    private FishingStrategyProfile parseProfile(StrategyRun run) {
        try {
            return objectMapper.convertValue(run.getStrategyProfile(), FishingStrategyProfile.class);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Strategy profile is not parseable");
        }
    }

    private WeatherContext parseWeather(StrategyRun run) {
        if (run.getWeatherSnapshot() == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(run.getWeatherSnapshot(), WeatherContext.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasLimitation(FishingStrategyProfile profile, DataLimitationCode code) {
        for (DataLimitation limitation : profile.dataLimitations()) {
            if (limitation.code() == code) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> rankingConfig() {
        PlanningProperties.Ranking ranking = properties.getRanking();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("strategyMatch", ranking.getStrategyMatch());
        map.put("depthMatch", ranking.getDepthMatch());
        map.put("featureConfidence", ranking.getFeatureConfidence());
        map.put("timeWindowMatch", ranking.getTimeWindowMatch());
        map.put("gearCompatibility", ranking.getGearCompatibility());
        map.put("weatherCompatibility", ranking.getWeatherCompatibility());
        map.put("travelAccess", ranking.getTravelAccess());
        map.put("historicalPerformance", ranking.getHistoricalPerformance());
        map.put("algorithmVersion", properties.getAlgorithmVersion());
        map.put("scheduleSlotMinutes", properties.getSchedule().getSlotMinutes());
        map.put("beamWidth", properties.getSchedule().getBeamWidth());
        map.put("waitPenalty", properties.getSchedule().getWaitPenalty());
        return map;
    }

    private Map<String, Object> boatCapabilitySnapshot(
            ResolvedBoatCapability baseline,
            EffectiveBoatCapability effective
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fingerprint", baseline.fingerprint());
        map.put("resolverVersion", baseline.resolverVersion());
        map.put("baseline", baseline.snapshot());
        if (effective != null) {
            map.put("effective", effective.snapshot());
            Map<String, Object> usable = new LinkedHashMap<>();
            usable.put("estimatedPractical", effective.estimatedPracticalRangeKm());
            usable.put("systemUsable", effective.systemUsableRangeKm());
            usable.put("comfortableCap", effective.comfortableCapKm());
            usable.put("effectiveUsable", effective.effectiveUsableRangeKm());
            map.put("usableRange", usable);
        }
        return map;
    }

    private Map<String, Object> filterSummary(
            Map<RejectionReason, Integer> rejections,
            int generated,
            int accepted,
            int waypoints
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generated", generated);
        summary.put("accepted", accepted);
        summary.put("waypoints", waypoints);
        Map<String, Integer> byReason = new LinkedHashMap<>();
        rejections.forEach((reason, count) -> byReason.put(reason.name(), count));
        summary.put("rejections", byReason);
        return summary;
    }

    private GeneratePlanResponse fail(
            PlanningRun running,
            Map<String, Object> inputSnapshot,
            Map<String, Object> rankingConfig,
            Map<RejectionReason, Integer> rejections,
            List<String> warnings,
            String error,
            UUID userId,
            String idempotencyKey
    ) {
        UUID lakeId = parseUuid(inputSnapshot.get("lakeId"));
        UUID snapshotId = parseUuid(inputSnapshot.get("spatialPlanningSnapshotId"));
        String pipeline = running.getFeaturePipeline() == null ? null : running.getFeaturePipeline().name();
        Map<String, Object> usage = new LinkedHashMap<>();
        if (GenerateProfiler.attached()) {
            usage.put("profiler", finishAndLogProfile(
                    GenerateProfiler.current(), running, lakeId, pipeline, snapshotId));
        }
        PlanningRun failed = persistence.fail(
                running.getId(),
                inputSnapshot,
                rankingConfig,
                filterSummary(rejections, 0, 0, 0),
                warnings,
                usage,
                error
        );
        if (running.getClientChannel() == ClientChannel.WEB) {
            webPlanQuotaService.markFailed(userId, idempotencyKey, running.getId());
        }
        return assembler.toGenerateResponse(failed, null, List.of());
    }

    private GenerateProfiler attachExecuteProfiler(GenerateAcceptance started) {
        GenerateProfiler incoming = started.profiler();
        if (incoming != null && GenerateProfiler.attached()) {
            return incoming;
        }
        GenerateProfiler worker = GenerateProfiler.begin();
        worker.mergeFrom(incoming);
        return worker;
    }

    private Map<String, Object> finishAndLogProfile(
            GenerateProfiler profiler,
            PlanningRun running,
            UUID lakeId,
            String pipeline,
            UUID snapshotId
    ) {
        profiler.end(GenerateProfiler.TOTAL_GENERATE);
        profiler.set("strategyAiMs", profiler.stageMs(GenerateProfiler.STRATEGY_AI));
        profiler.set("tacticsAiMs", profiler.stageMs(GenerateProfiler.TACTICS_AI));
        profiler.set("transitMaterializeMs", profiler.stageMs(GenerateProfiler.TRANSIT_MATERIALIZATION));
        log.info("GENERATE_PROFILE {}", profiler.generateLogJson(
                running == null ? null : running.getId(),
                running == null ? null : running.getTripId(),
                lakeId,
                pipeline,
                snapshotId
        ));
        return profiler.snapshot();
    }

    private void flushZoneWaterPaths(PlanningContext context) {
        if (context == null) {
            return;
        }
        try {
            snapshotWaterPathService.flushPending(context.pendingZoneWaterPaths());
        } catch (RuntimeException ex) {
            log.warn("Zone water-path flush failed: {}", ex.getMessage());
        }
    }

    private static UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Trip requireOwned(UUID tripId) {
        return tripRepository.findByIdAndUserId(tripId, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Trip not found"));
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
