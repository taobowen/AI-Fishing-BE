package com.aifishing.planning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.PlanningMode;
import com.aifishing.fishingtemplate.domain.FishingTemplate;
import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.fishingtemplate.repo.FishingTemplateRepository;
import com.aifishing.fishingtemplate.repo.FishingTemplateTargetRepository;
import com.aifishing.lake.ingestion.repo.LakeBoundaryRecordRepository;
import com.aifishing.lake.ingestion.repo.LakeDatasetStatusRepository;
import com.aifishing.lake.ingestion.repo.LakeWaterwayRepository;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.repo.PlanningRunRepository;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;
import com.aifishing.planning.repo.TripPlanningInputTargetRepository;
import com.aifishing.planning.service.TripPlanningInputSnapshotService;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.repo.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripPlanningInputSnapshotIT extends AbstractIntegrationTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double WATER_LNG = PlanningFixtures.HEAD_LNG + 0.002;
    private static final double WATER_LAT = PlanningFixtures.HEAD_LAT;

    @Autowired
    private TripPlanningInputSnapshotService snapshotService;
    @Autowired
    private TripPlanningInputSnapshotRepository snapshotRepository;
    @Autowired
    private TripPlanningInputTargetRepository inputTargetRepository;
    @Autowired
    private FishingTemplateRepository templateRepository;
    @Autowired
    private FishingTemplateTargetRepository templateTargetRepository;
    @Autowired
    private PlanningRunRepository planningRunRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private LakeBoundaryRecordRepository boundaryRepository;
    @Autowired
    private LakeWaterwayRepository waterwayRepository;
    @Autowired
    private LakeDatasetStatusRepository statusRepository;

    @BeforeEach
    void seedWater() {
        ProcessingFixtures.seedShorelineOnly(
                DevSeedIds.LAKE_ID,
                PlanningFixtures.HEAD_LAT,
                PlanningFixtures.HEAD_LNG,
                waterwayRepository,
                boundaryRepository,
                statusRepository
        );
    }

    @Test
    @Transactional
    void frozenTargetGeometryIsUnchangedAfterLiveTemplateEdit() {
        Point original = point(WATER_LNG, WATER_LAT);
        FishingTemplate template = new FishingTemplate();
        template.setUserId(DevSeedIds.USER_ID);
        template.setLakeId(DevSeedIds.LAKE_ID);
        template.setName("Snapshot fixture");
        template = templateRepository.save(template);

        FishingTemplateTarget target = new FishingTemplateTarget();
        target.setTemplateId(template.getId());
        target.setKind(TemplateTargetKind.POINT);
        target.setName("Original");
        target.setGeometry(original);
        target.setSortOrder(0);
        target = templateTargetRepository.save(target);

        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, com.aifishing.common.enums.FishingMode.SHORE);
        trip.setPlanningMode(PlanningMode.CUSTOM);
        trip.setFishingTemplateId(template.getId());
        trip = tripRepository.save(trip);

        PlanningRun run = new PlanningRun();
        run.setId(UUID.randomUUID());
        run.setTripId(trip.getId());
        run.setStatus(PlanningRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run = planningRunRepository.save(run);

        snapshotService.freezeForRun(trip, run.getId());
        var snapshot = snapshotRepository.findByPlanningRunId(run.getId()).orElseThrow();
        List<TripPlanningInputTarget> frozen =
                inputTargetRepository.findBySnapshotIdOrderBySortOrderAscIdAsc(snapshot.getId());
        assertThat(frozen).hasSize(1);
        Point frozenPoint = (Point) frozen.getFirst().getGeometry();
        assertThat(frozenPoint.getX()).isEqualTo(original.getX());
        assertThat(frozenPoint.getY()).isEqualTo(original.getY());

        Point moved = point(WATER_LNG + 0.001, WATER_LAT + 0.001);
        target.setGeometry(moved);
        target.setName("Edited");
        templateTargetRepository.saveAndFlush(target);
        entityManager.clear();

        List<TripPlanningInputTarget> afterEdit =
                inputTargetRepository.findBySnapshotIdOrderBySortOrderAscIdAsc(snapshot.getId());
        assertThat(afterEdit).hasSize(1);
        Point stillFrozen = (Point) afterEdit.getFirst().getGeometry();
        assertThat(stillFrozen.getX()).isEqualTo(original.getX());
        assertThat(stillFrozen.getY()).isEqualTo(original.getY());
        assertThat(afterEdit.getFirst().getName()).isEqualTo("Original");

        FishingTemplateTarget live = templateTargetRepository.findById(target.getId()).orElseThrow();
        assertThat(((Point) live.getGeometry()).getX()).isEqualTo(moved.getX());
    }

    private static Point point(double lng, double lat) {
        Point point = FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);
        return point;
    }
}
