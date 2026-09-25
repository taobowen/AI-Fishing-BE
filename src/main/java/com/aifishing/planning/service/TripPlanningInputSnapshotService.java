package com.aifishing.planning.service;

import com.aifishing.common.enums.PlanningMode;
import com.aifishing.fishingtemplate.domain.FishingTemplate;
import com.aifishing.fishingtemplate.domain.FishingTemplateTarget;
import com.aifishing.fishingtemplate.domain.TemplateTargetKind;
import com.aifishing.fishingtemplate.repo.FishingTemplateRepository;
import com.aifishing.fishingtemplate.repo.FishingTemplateTargetRepository;
import com.aifishing.planning.domain.PlanningInputTargetSource;
import com.aifishing.planning.domain.TripPlanningInputSnapshot;
import com.aifishing.planning.domain.TripPlanningInputTarget;
import com.aifishing.planning.repo.TripPlanningInputSnapshotRepository;
import com.aifishing.planning.repo.TripPlanningInputTargetRepository;
import com.aifishing.trip.domain.Trip;
import com.aifishing.trip.domain.TripRequiredPoint;
import com.aifishing.trip.repo.TripRequiredPointRepository;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Freezes the trip's live template geometry and required points onto a planning run.
 * Later edits to the live template do not change historical snapshot rows.
 * Call from {@link TripPlanningService} once per generate run.
 */
@Service
public class TripPlanningInputSnapshotService {

    private final TripPlanningInputSnapshotRepository snapshotRepository;
    private final TripPlanningInputTargetRepository targetRepository;
    private final FishingTemplateRepository templateRepository;
    private final FishingTemplateTargetRepository templateTargetRepository;
    private final TripRequiredPointRepository requiredPointRepository;

    public TripPlanningInputSnapshotService(
            TripPlanningInputSnapshotRepository snapshotRepository,
            TripPlanningInputTargetRepository targetRepository,
            FishingTemplateRepository templateRepository,
            FishingTemplateTargetRepository templateTargetRepository,
            TripRequiredPointRepository requiredPointRepository
    ) {
        this.snapshotRepository = snapshotRepository;
        this.targetRepository = targetRepository;
        this.templateRepository = templateRepository;
        this.templateTargetRepository = templateTargetRepository;
        this.requiredPointRepository = requiredPointRepository;
    }

    @Transactional
    public TripPlanningInputSnapshot freezeForRun(Trip trip, UUID planningRunId) {
        if (snapshotRepository.findByPlanningRunId(planningRunId).isPresent()) {
            return snapshotRepository.findByPlanningRunId(planningRunId).orElseThrow();
        }
        PlanningMode mode = PlanningMode.orAi(trip.getPlanningMode());
        FishingTemplate template = null;
        List<FishingTemplateTarget> templateTargets = List.of();
        if (trip.getFishingTemplateId() != null) {
            template = templateRepository.findById(trip.getFishingTemplateId()).orElse(null);
            if (template != null) {
                templateTargets = templateTargetRepository.findByTemplateIdOrderBySortOrderAscIdAsc(template.getId());
            }
        }
        List<TripRequiredPoint> requiredPoints =
                requiredPointRepository.findByTripIdOrderBySortOrderAscIdAsc(trip.getId());

        TripPlanningInputSnapshot snapshot = new TripPlanningInputSnapshot();
        snapshot.setPlanningRunId(planningRunId);
        snapshot.setMode(mode);
        snapshot.setTemplateId(template == null ? null : template.getId());
        snapshot.setTemplateName(template == null ? null : template.getName());
        snapshot.setTemplateTargetCount(templateTargets.size());
        snapshot.setRequiredPointCount(requiredPoints.size());
        TripPlanningInputSnapshot saved = snapshotRepository.save(snapshot);

        List<TripPlanningInputTarget> rows = new ArrayList<>();
        int order = 0;
        for (FishingTemplateTarget target : templateTargets) {
            rows.add(copyTemplateTarget(saved.getId(), target, order++));
        }
        for (TripRequiredPoint point : requiredPoints) {
            rows.add(copyRequiredPoint(saved.getId(), point, order++));
        }
        if (!rows.isEmpty()) {
            targetRepository.saveAll(rows);
        }
        return saved;
    }

    private static TripPlanningInputTarget copyTemplateTarget(
            UUID snapshotId,
            FishingTemplateTarget source,
            int sortOrder
    ) {
        TripPlanningInputTarget row = new TripPlanningInputTarget();
        row.setSnapshotId(snapshotId);
        row.setSource(PlanningInputTargetSource.TEMPLATE);
        row.setKind(source.getKind());
        row.setName(source.getName());
        row.setGeometry(copyGeometry(source.getGeometry()));
        row.setSortOrder(sortOrder);
        row.setOriginTemplateTargetId(source.getId());
        return row;
    }

    private static TripPlanningInputTarget copyRequiredPoint(
            UUID snapshotId,
            TripRequiredPoint source,
            int sortOrder
    ) {
        TripPlanningInputTarget row = new TripPlanningInputTarget();
        row.setSnapshotId(snapshotId);
        row.setSource(PlanningInputTargetSource.REQUIRED_POINT);
        row.setKind(TemplateTargetKind.POINT);
        row.setName(source.getLabel());
        row.setGeometry(copyGeometry(source.getLocation()));
        row.setSortOrder(sortOrder);
        row.setOriginRequiredPointId(source.getId());
        return row;
    }

    private static Geometry copyGeometry(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        Geometry copy = geometry.copy();
        copy.setSRID(geometry.getSRID() == 0 ? 4326 : geometry.getSRID());
        return copy;
    }
}
