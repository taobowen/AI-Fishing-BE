package com.aifishing.planning.filter;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.lake.domain.Lake;
import com.aifishing.lake.ingestion.domain.FishingRestriction;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.PlanningProperties;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.candidate.LakePlanningGeometry;
import com.aifishing.planning.route.AccessResolution;
import com.aifishing.planning.service.PlanningContext;
import com.aifishing.strategy.StrategyFixtures;
import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationFilterTest {

    private final RegulationFilter filter = new RegulationFilter();
    private final double lat = PlanningFixtures.HEAD_LAT;
    private final double lng = PlanningFixtures.HEAD_LNG;

    @Test
    void wholeAreaSanctuaryRejectsCoveredCandidate() {
        Polygon zone = ProcessingFixtures.polygonSquare(lng, lat, 80);
        FishingRestriction restriction = PlanningFixtures.restriction(
                UUID.randomUUID(), "SANCTUARY", null, zone, "sanctuary");
        CandidateSpot spot = spotIn(zone);
        FilterResult result = filter.apply(spot, context(List.of(restriction), FishingMode.BOAT));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReason.REGULATION_WHOLE_AREA);
    }

    @Test
    void primarySpeciesRestrictionRejects() {
        Polygon zone = ProcessingFixtures.polygonSquare(lng, lat, 80);
        FishingRestriction restriction = PlanningFixtures.restriction(
                UUID.randomUUID(), "CLOSED", FishSpecies.SMALLMOUTH_BASS, zone, "primary");
        FilterResult result = filter.apply(spotIn(zone), context(List.of(restriction), FishingMode.BOAT));
        assertThat(result.reason()).isEqualTo(RejectionReason.REGULATION_PRIMARY_SPECIES);
    }

    @Test
    void secondaryOnlyRestrictionKeepsCandidateAndWarns() {
        Polygon zone = ProcessingFixtures.polygonSquare(lng, lat, 80);
        FishingRestriction restriction = PlanningFixtures.restriction(
                UUID.randomUUID(), "CLOSED", FishSpecies.WALLEYE, zone, "secondary");
        CandidateSpot spot = spotIn(zone);
        FilterResult result = filter.apply(spot, context(List.of(restriction), FishingMode.BOAT));
        assertThat(result.accepted()).isTrue();
        assertThat(spot.isSecondaryTargetRestricted()).isTrue();
        assertThat(result.warning()).isEqualTo("secondaryTargetRestricted");
    }

    @Test
    void rawTextWithoutGeometryIsIgnored() {
        FishingRestriction restriction = PlanningFixtures.restriction(
                UUID.randomUUID(), "SANCTUARY", null, null, "text-only");
        restriction.setGeometry(null);
        restriction.setRawText("Sanctuary west of the creek mouth until further notice");
        CandidateSpot spot = spotIn(ProcessingFixtures.polygonSquare(lng, lat, 40));
        FilterResult result = filter.apply(spot, context(List.of(restriction), FishingMode.BOAT));
        assertThat(result.accepted()).isTrue();
        assertThat(spot.isSecondaryTargetRestricted()).isFalse();
    }

    private CandidateSpot spotIn(Polygon zone) {
        CandidateSpot spot = new CandidateSpot();
        Point point = zone.getInteriorPoint();
        point.setSRID(4326);
        spot.setLocation(point);
        return spot;
    }

    private PlanningContext context(List<FishingRestriction> restrictions, FishingMode mode) {
        Trip trip = PlanningFixtures.trip(UUID.randomUUID(), UUID.randomUUID(), mode);
        Lake lake = new Lake();
        lake.setId(trip.getLakeId());
        return new PlanningContext(
                trip,
                lake,
                new Boat(),
                AccessResolution.unknown(),
                new LakePlanningGeometry(ProcessingFixtures.polygonSquare(lng, lat, 500), List.of()),
                restrictions,
                "AVAILABLE",
                PlanningFixtures.forecast(),
                List.of(),
                StrategyFixtures.validProfile(),
                new StrategyRun(),
                new PlanningProperties(),
                new ArrayList<>()
        );
    }
}
