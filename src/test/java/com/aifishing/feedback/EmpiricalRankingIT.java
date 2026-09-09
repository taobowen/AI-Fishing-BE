package com.aifishing.feedback;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.common.enums.FishSpecies;
import com.aifishing.common.enums.FishingMode;
import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.enums.TripPlanStatus;
import com.aifishing.common.geo.GeoPointDto;
import com.aifishing.feedback.catchlog.domain.CatchAssociationMethod;
import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchOutcome;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import com.aifishing.feedback.catchlog.repo.CatchEventRepository;
import com.aifishing.feedback.effort.domain.EffortSegmentType;
import com.aifishing.feedback.effort.domain.FishingEffortSegment;
import com.aifishing.feedback.effort.repo.FishingEffortSegmentRepository;
import com.aifishing.feedback.ranking.EmpiricalEvidence;
import com.aifishing.feedback.ranking.EmpiricalRankingProvider;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.lake.processing.ProcessingFixtures;
import com.aifishing.lake.processing.domain.LakeFeature;
import com.aifishing.lake.processing.dto.FeatureType;
import com.aifishing.lake.processing.repo.LakeFeatureRepository;
import com.aifishing.planning.PlanningFixtures;
import com.aifishing.planning.candidate.CandidateSpot;
import com.aifishing.planning.domain.TripPlan;
import com.aifishing.seed.DevSeedIds;
import com.aifishing.trip.domain.Trip;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmpiricalRankingIT extends AbstractIntegrationTest {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired
    EmpiricalRankingProvider rankingProvider;
    @Autowired
    LakeFeatureRepository featureRepository;
    @Autowired
    FishingEffortSegmentRepository segmentRepository;
    @Autowired
    CatchEventRepository catchEventRepository;

    @Test
    void sparseEffortIsNeutralAndTrackOverlapDoesNotAssignWholeSegment() {
        LakeFeature near = featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 40),
                2,
                4,
                0.9,
                "rank-v1"
        ));
        LakeFeature far = featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT + 0.05, 40),
                2,
                4,
                0.9,
                "rank-v1"
        ));
        FishingSession session = completedSession(FishSpecies.SMALLMOUTH_BASS);
        LineString longTrack = line(
                PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT,
                PlanningFixtures.HEAD_LNG + 0.02, PlanningFixtures.HEAD_LAT
        );
        saveFishing(session, 3600, longTrack, geoMapper.toPoint(new GeoPointDto(PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)));
        saveLanded(session, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG);

        Map<UUID, EmpiricalEvidence> scores = rankingProvider.scoreCandidates(
                DevSeedIds.LAKE_ID,
                FishSpecies.SMALLMOUTH_BASS,
                DevSeedIds.USER_ID,
                List.of(spot(near.getId()), spot(far.getId()))
        );
        assertThat(scores.get(near.getId()).historicalPerformance()).isEqualTo(0.5);
        assertThat(scores.get(near.getId()).historicalEvidenceConfidence()).isEqualTo(0);
        assertThat(scores.get(far.getId()).historicalPerformance()).isEqualTo(0.5);
        assertThat(scores.get(far.getId()).historicalEvidenceConfidence()).isEqualTo(0);
    }

    @Test
    void sufficientOverlappingEffortMovesScoreAndIgnoresOtherSpecies() {
        LakeFeature feature = featureRepository.save(PlanningFixtures.feature(
                DevSeedIds.LAKE_ID,
                FeatureType.HUMP,
                ProcessingFixtures.polygonSquare(PlanningFixtures.HEAD_LNG, PlanningFixtures.HEAD_LAT, 80),
                2,
                4,
                0.9,
                "rank-v1"
        ));
        FishingSession bass = completedSession(FishSpecies.SMALLMOUTH_BASS);
        saveFishing(bass, 20 * 60, null, geoMapper.toPoint(new GeoPointDto(PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)));
        saveLanded(bass, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG);
        saveLanded(bass, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG);

        Trip walleyeTrip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        walleyeTrip.setPrimaryTargetSpecies(FishSpecies.WALLEYE);
        tripRepository.save(walleyeTrip);
        FishingSession walleye = new FishingSession();
        walleye.setTripId(walleyeTrip.getId());
        walleye.setUserId(DevSeedIds.USER_ID);
        walleye.setStartedAt(Instant.parse("2026-09-02T10:00:00Z"));
        walleye.setEndedAt(Instant.parse("2026-09-02T12:00:00Z"));
        walleye.setStatus(FishingSessionStatus.COMPLETED);
        fishingSessionRepository.save(walleye);
        saveFishing(walleye, 40 * 60, null, geoMapper.toPoint(new GeoPointDto(PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG)));
        saveLanded(walleye, PlanningFixtures.HEAD_LAT, PlanningFixtures.HEAD_LNG);

        Map<UUID, EmpiricalEvidence> scores = rankingProvider.scoreCandidates(
                DevSeedIds.LAKE_ID,
                FishSpecies.SMALLMOUTH_BASS,
                DevSeedIds.USER_ID,
                List.of(spot(feature.getId()))
        );
        EmpiricalEvidence evidence = scores.get(feature.getId());
        assertThat(evidence.historicalEvidenceConfidence()).isGreaterThan(0);
        assertThat(evidence.historicalPerformance()).isNotEqualTo(0.5);
    }

    private FishingSession completedSession(FishSpecies species) {
        Trip trip = PlanningFixtures.trip(DevSeedIds.USER_ID, DevSeedIds.LAKE_ID, FishingMode.SHORE);
        trip.setPrimaryTargetSpecies(species);
        tripRepository.save(trip);
        TripPlan plan = new TripPlan();
        plan.setTripId(trip.getId());
        plan.setVersion(1);
        plan.setStatus(TripPlanStatus.GENERATED);
        plan.setGeneratedAt(Instant.parse("2026-09-02T12:00:00Z"));
        plan.setPlanningAlgorithmVersion("rank-test");
        tripPlanRepository.save(plan);
        FishingSession session = new FishingSession();
        session.setTripId(trip.getId());
        session.setUserId(DevSeedIds.USER_ID);
        session.setTripPlanId(plan.getId());
        session.setStartedAt(Instant.parse("2026-09-02T14:00:00Z"));
        session.setEndedAt(Instant.parse("2026-09-02T16:00:00Z"));
        session.setStatus(FishingSessionStatus.COMPLETED);
        return fishingSessionRepository.save(session);
    }

    private void saveFishing(FishingSession session, int seconds, LineString track, org.locationtech.jts.geom.Point rep) {
        FishingEffortSegment segment = new FishingEffortSegment();
        segment.setFishingSessionId(session.getId());
        segment.setSegmentType(EffortSegmentType.FISHING);
        segment.setStartedAt(session.getStartedAt());
        segment.setEndedAt(session.getStartedAt().plusSeconds(seconds));
        segment.setDurationSeconds(seconds);
        segment.setTrackGeometry(track);
        segment.setRepresentativeLocation(rep);
        segment.setConfidence(BigDecimal.ONE);
        segment.setDerivationVersion("effort-v1");
        segmentRepository.save(segment);
    }

    private void saveLanded(FishingSession session, double lat, double lng) {
        CatchEvent catchEvent = new CatchEvent();
        catchEvent.setUserId(session.getUserId());
        catchEvent.setFishingSessionId(session.getId());
        catchEvent.setTripId(session.getTripId());
        catchEvent.setTripPlanId(session.getTripPlanId());
        catchEvent.setClientCatchId(UUID.randomUUID().toString());
        catchEvent.setOccurredAt(session.getStartedAt().plusSeconds(30));
        catchEvent.setReceivedAt(session.getStartedAt().plusSeconds(30));
        catchEvent.setLocation(geoMapper.toPoint(new GeoPointDto(lat, lng)));
        catchEvent.setAssociationMethod(CatchAssociationMethod.UNASSOCIATED);
        catchEvent.setStatus(CatchStatus.ACTIVE);
        catchEvent.setOutcome(CatchOutcome.LANDED);
        catchEvent.setPrimaryTargetSpecies(FishSpecies.SMALLMOUTH_BASS);
        catchEventRepository.save(catchEvent);
    }

    private static CandidateSpot spot(UUID featureId) {
        CandidateSpot spot = new CandidateSpot();
        spot.setFeatureId(featureId);
        return spot;
    }

    private static LineString line(double lng1, double lat1, double lng2, double lat2) {
        LineString line = FACTORY.createLineString(new Coordinate[]{
                new Coordinate(lng1, lat1),
                new Coordinate(lng2, lat2)
        });
        line.setSRID(4326);
        return line;
    }
}
