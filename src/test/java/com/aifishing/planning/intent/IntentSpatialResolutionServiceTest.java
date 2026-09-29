package com.aifishing.planning.intent;

import com.aifishing.fishingtemplate.domain.FishingTemplate;
import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.fishingtemplate.repo.FishingTemplateRepository;
import com.aifishing.fishingtemplate.repo.FishingTemplateTargetRepository;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.repo.LakeRepository;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.LakePlanningGeometryLoader;
import com.aifishing.planning.domain.PlanningInputTargetSource;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.SpatialSnapshotStatus;
import com.aifishing.planning.spatial.SpatialSnapshotView;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.SpatialPlanningSnapshotRepository;
import com.aifishing.trip.repo.TripRepository;
import com.aifishing.trip.repo.TripRequiredPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentSpatialResolutionServiceTest {

    private IntentSpatialResolutionRepository liveRepository;
    private TripPlanningIntentResolutionRepository runRepository;
    private FishingTemplateRepository templateRepository;
    private FishingTemplateTargetRepository templateTargetRepository;
    private SpatialPlanningSnapshotRepository snapshotRepository;
    private LakeFishingTargetRepository fishingTargetRepository;
    private LakeRepository lakeRepository;
    private IntentSpatialResolutionService service;

    @BeforeEach
    void setUp() {
        liveRepository = mock(IntentSpatialResolutionRepository.class);
        runRepository = mock(TripPlanningIntentResolutionRepository.class);
        templateRepository = mock(FishingTemplateRepository.class);
        templateTargetRepository = mock(FishingTemplateTargetRepository.class);
        snapshotRepository = mock(SpatialPlanningSnapshotRepository.class);
        fishingTargetRepository = mock(LakeFishingTargetRepository.class);
        lakeRepository = mock(LakeRepository.class);
        service = new IntentSpatialResolutionService(
                new PlanningProperties(),
                liveRepository,
                runRepository,
                templateRepository,
                templateTargetRepository,
                mock(TripRequiredPointRepository.class),
                mock(TripRepository.class),
                lakeRepository,
                mock(LakePlanningGeometryLoader.class),
                snapshotRepository,
                fishingTargetRepository
        );
    }

    @Test
    void freshCacheDoesNotScanTheSnapshot() {
        UUID originId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID fishingTargetId = UUID.randomUUID();
        IntentSpatialResolution cached = cachedRow(originId, snapshotId, fishingTargetId);
        when(liveRepository.findTemplate(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(cached));
        when(runRepository.findByPlanningInputTargetIdOrderByRankAsc(any())).thenReturn(List.of());

        List<IntentMatch> matches = service.matches(frozenPoint(originId), context(snapshotId));

        assertThat(matches).extracting(IntentMatch::fishingTargetId).containsExactly(fishingTargetId);
        verify(fishingTargetRepository, never()).findBySpatialPlanningSnapshotId(any());
        verify(liveRepository, never()).saveAll(any());
        ArgumentCaptor<List<TripPlanningIntentResolution>> saved = ArgumentCaptor.forClass(List.class);
        verify(runRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().get(0).getOriginTemplateTargetId()).isEqualTo(originId);
        assertThat(saved.getValue().get(0).getPlanningInputTargetId()).isNotNull();
        verify(templateTargetRepository, never()).findById(any());
    }

    @Test
    void staleSnapshotRepairsOnceThenHitsTheCache() {
        UUID originId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        List<IntentSpatialResolution> stored = new ArrayList<>();
        List<TripPlanningIntentResolution> runs = new ArrayList<>();
        when(liveRepository.findTemplate(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenAnswer(invocation -> List.copyOf(stored));
        when(liveRepository.saveAll(any())).thenAnswer(invocation -> {
            stored.clear();
            stored.addAll(invocation.getArgument(0));
            return stored;
        });
        when(runRepository.findByPlanningInputTargetIdOrderByRankAsc(any()))
                .thenAnswer(invocation -> List.copyOf(runs));
        when(runRepository.saveAll(any())).thenAnswer(invocation -> {
            runs.addAll(invocation.getArgument(0));
            return runs;
        });

        TripPlanningInputTarget target = frozenPoint(originId);
        PlanningContext context = context(snapshotId);
        List<IntentMatch> repaired = service.matches(target, context);
        List<IntentMatch> cached = service.matches(target, context);

        assertThat(repaired).allMatch(IntentMatch::syntheticFallback);
        assertThat(cached).allMatch(IntentMatch::syntheticFallback);
        verify(liveRepository, times(1)).saveAll(any());
        verify(fishingTargetRepository, never()).findBySpatialPlanningSnapshotId(any());
        verify(runRepository, times(1)).saveAll(any());
    }

    @Test
    void noReadySnapshotLeavesTheLiveCacheEmpty() {
        UUID targetId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID lakeId = UUID.randomUUID();
        FishingTemplateTarget target = new FishingTemplateTarget();
        target.setId(targetId);
        target.setTemplateId(templateId);
        target.setKind(TemplateTargetKind.POINT);
        target.setGeometry(RoutePlannerHarness.point(-78.92, 44.75));
        FishingTemplate template = new FishingTemplate();
        template.setId(templateId);
        template.setLakeId(lakeId);
        Lake lake = new Lake();
        lake.setId(lakeId);
        when(templateTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(lakeRepository.findById(lakeId)).thenReturn(Optional.of(lake));
        when(snapshotRepository.findByLakeIdAndTargetDerivationVersionAndZoneBuilderVersionAndNavigationVersionAndStatusOrderByCreatedAtDesc(
                any(), any(), any(), any(), any(SpatialSnapshotStatus.class)
        )).thenReturn(List.of());

        service.resolveTemplateTargets(List.of(targetId));

        verify(liveRepository, never()).saveAll(any());
        verify(fishingTargetRepository, never()).findBySpatialPlanningSnapshotId(any());
    }

    private static IntentSpatialResolution cachedRow(UUID originId, UUID snapshotId, UUID fishingTargetId) {
        IntentMatchConfig config = IntentMatchConfig.from(new PlanningProperties().getSpatial());
        IntentSpatialResolution row = new IntentSpatialResolution();
        row.setOriginKind(IntentOriginKind.TEMPLATE_TARGET);
        row.setOriginTemplateTargetId(originId);
        row.setSpatialSnapshotId(snapshotId);
        row.setMatchingVersion(config.matchingVersion());
        row.setTemplatePointRadiusM(config.templatePointRadiusM());
        row.setTemplatePathCorridorM(config.templatePathCorridorM());
        row.setRequiredPointRadiusM(config.requiredPointRadiusM());
        row.setMaxMatches(config.maxMatches());
        row.setRank(1);
        row.setFishingTargetId(fishingTargetId);
        row.setDistanceM(12.0);
        row.setSyntheticFallback(false);
        return row;
    }

    private static TripPlanningInputTarget frozenPoint(UUID originId) {
        TripPlanningInputTarget target = new TripPlanningInputTarget();
        target.setId(UUID.randomUUID());
        target.setSource(PlanningInputTargetSource.TEMPLATE);
        target.setKind(TemplateTargetKind.POINT);
        target.setOriginTemplateTargetId(originId);
        target.setGeometry(RoutePlannerHarness.point(-78.92, 44.75));
        return target;
    }

    private static PlanningContext context(UUID snapshotId) {
        PlanningContext base = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(RoutePlannerHarness.hour(8, 0, 8, 20, 600)), 8, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(snapshotId);
        return base.withSnapshot(new SpatialSnapshotView(
                snapshot, List.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of()));
    }
}
