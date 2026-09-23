package com.aifishing.planning.spatial;

import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.route.PlannedStop;
import com.aifishing.planning.route.RoutePlanner;
import com.aifishing.planning.route.RoutePlannerHarness;
import com.aifishing.planning.route.TravelEstimate;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.planning.spatial.domain.SpatialPlanningSnapshot;
import com.aifishing.planning.spatial.domain.TripPlanTransitLeg;
import com.aifishing.strategy.domain.LightPreference;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransitLegMaterializerTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void persistsPathMetersWithoutRewritingPlannerMinutes() {
        LineString water = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(-78.92, 44.75),
                new Coordinate(-78.91, 44.751),
                new Coordinate(-78.90, 44.752)
        });
        TransitLegMaterializer materializer = new TransitLegMaterializer(
                new StubWaterPaths(Optional.of(new LocalWaterPathEstimator.PathEstimate(water, 2400, 99))),
                null,
                new PlanningProperties()
        );

        PlannedStop stop = stop(new TravelEstimate(900, 12.5, 1.15, false));
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 2, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        ).withSnapshot(snapshotView());
        RoutePlanner.RouteResult route = new RoutePlanner.RouteResult(
                List.of(stop),
                false,
                Instant.parse("2026-09-08T12:45:00Z"),
                Instant.parse("2026-09-08T14:10:00Z"),
                new TravelEstimate(800, 10, 1.1, false),
                List.of(),
                0,
                40,
                22.5,
                0,
                0
        );

        List<TripPlanTransitLeg> legs = materializer.materialize(route, context);

        assertThat(legs).hasSize(2);
        assertThat(legs.getFirst().getFromKind()).isEqualTo(TransitEndpointKind.LAUNCH);
        assertThat(legs.getFirst().getToKind()).isEqualTo(TransitEndpointKind.VISIT);
        assertThat(legs.getFirst().getPathDistanceMeters()).isEqualByComparingTo("2400.00");
        assertThat(legs.getFirst().getPlannedTravelMinutes()).isEqualByComparingTo("12.50");
        assertThat(legs.getLast().getToKind()).isEqualTo(TransitEndpointKind.RETURN);
        assertThat(legs.getLast().getPlannedTravelMinutes()).isEqualByComparingTo("10.00");
        assertThat(stop.fromPrevious().minutes()).isEqualTo(12.5);
        assertThat(stop.arrivalAt()).isEqualTo(Instant.parse("2026-09-08T13:00:00Z"));
    }

    @Test
    void missingWaterPathStillKeepsPlannerMinutes() {
        TransitLegMaterializer materializer = new TransitLegMaterializer(
                new StubWaterPaths(Optional.empty()),
                null,
                new PlanningProperties()
        );
        PlannedStop stop = stop(new TravelEstimate(900, 12.5, 1.15, false));
        PlanningContext context = RoutePlannerHarness.context(
                RoutePlannerHarness.hourly(List.of(), 2, 20),
                new PlanningProperties(),
                RoutePlannerHarness.launch()
        );

        List<TripPlanTransitLeg> legs = materializer.materialize(
                new RoutePlanner.RouteResult(List.of(stop), false), context);

        assertThat(legs.getFirst().getTransitPath()).isNull();
        assertThat(legs.getFirst().getPlannedTravelMinutes()).isEqualByComparingTo("12.50");
    }

    @Test
    void transitKeysAreStableIdentitiesNotRoundedCoordinates() {
        UUID launchId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID visitId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertThat(TransitPathKeys.launch(launchId)).isEqualTo("launch:" + launchId);
        assertThat(TransitPathKeys.visitEntry(visitId)).isEqualTo("visit:" + visitId + ":entry");
        assertThat(TransitPathKeys.visitExit(visitId)).isEqualTo("visit:" + visitId + ":exit");
        assertThat(SpatialSnapshotService.pathIdentity("water-nav-v3", "launch:" + launchId, "visit:" + visitId + ":entry"))
                .isEqualTo("water-nav-v3|launch:" + launchId + "|visit:" + visitId + ":entry");
    }

    private static PlannedStop stop(TravelEstimate travel) {
        var candidate = RoutePlannerHarness.candidate(
                UUID.randomUUID(), -78.90, 44.752, 0.8, LightPreference.NEUTRAL, FeatureType.POINT);
        candidate.spot().setEntryPoint(RoutePlannerHarness.point(-78.90, 44.752));
        candidate.spot().setExitPoint(RoutePlannerHarness.point(-78.90, 44.752));
        return new PlannedStop(
                candidate,
                Instant.parse("2026-09-08T13:00:00Z"),
                Instant.parse("2026-09-08T13:40:00Z"),
                40,
                travel,
                null,
                List.of(),
                Map.of(),
                0,
                null
        );
    }

    private static SpatialSnapshotView snapshotView() {
        SpatialPlanningSnapshot snapshot = new SpatialPlanningSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setNavigationVersion("water-nav-v3");
        return new SpatialSnapshotView(
                snapshot,
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                null
        );
    }

    private static final class StubWaterPaths extends SnapshotWaterPathService {
        private final Optional<LocalWaterPathEstimator.PathEstimate> path;

        private StubWaterPaths(Optional<LocalWaterPathEstimator.PathEstimate> path) {
            super(null);
            this.path = path;
        }

        @Override
        public Optional<LocalWaterPathEstimator.PathEstimate> transitPath(
                SpatialSnapshotView view,
                String fromKey,
                String toKey,
                Point from,
                Point to,
                PlanningProperties.Spatial spatial
        ) {
            return path;
        }
    }
}
