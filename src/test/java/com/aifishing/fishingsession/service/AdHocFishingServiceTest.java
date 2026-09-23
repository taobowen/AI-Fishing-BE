package com.aifishing.fishingsession.service;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.common.exception.BadRequestException;
import com.aifishing.common.geo.GeoMapper;
import com.aifishing.fishingsession.SessionProperties;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.LocationQuality;
import com.aifishing.fishingsession.domain.SessionAdHocFishingStop;
import com.aifishing.fishingsession.domain.SessionLocationPoint;
import com.aifishing.fishingsession.dto.ClientEventRequest;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionLocationPointRepository;
import com.aifishing.guidance.events.ActivityStateUpdater;
import com.aifishing.guidance.events.SessionEventWriter;
import com.aifishing.planning.spatial.domain.LakeFishingTarget;
import com.aifishing.planning.spatial.domain.LakeFishingZoneMember;
import com.aifishing.planning.spatial.repo.LakeFishingTargetRepository;
import com.aifishing.planning.spatial.repo.LakeFishingZoneMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdHocFishingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T18:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), GeoMapper.SRID);

    @Mock
    private SessionAdHocFishingStopRepository stopRepository;
    @Mock
    private SessionLocationPointRepository locationPointRepository;
    @Mock
    private LakeFishingTargetRepository fishingTargetRepository;
    @Mock
    private LakeFishingZoneMemberRepository zoneMemberRepository;
    @Mock
    private ActivityStateUpdater activityStateUpdater;
    @Mock
    private SessionEventWriter sessionEventWriter;

    private AdHocFishingService service;

    @BeforeEach
    void setUp() {
        service = new AdHocFishingService(
                new SessionProperties(),
                stopRepository,
                locationPointRepository,
                fishingTargetRepository,
                zoneMemberRepository,
                activityStateUpdater,
                sessionEventWriter
        );
    }

    @Test
    void duplicateClientEventIdReturnsExistingStopWithoutInsert() {
        SessionAdHocFishingStop existing = openStop("client-1");
        when(stopRepository.findByFishingSessionIdAndClientEventId(SESSION_ID, "client-1"))
                .thenReturn(Optional.of(existing));

        SessionAdHocFishingStop result = service.start(activeSession(), request("client-1"));

        assertThat(result).isSameAs(existing);
        verify(stopRepository, never()).saveAndFlush(any());
        verify(sessionEventWriter).onAdHocStarted(any(), any(), any());
        verify(activityStateUpdater).refresh(any(), any());
    }

    @Test
    void secondStartWhileOpenReturnsOpenStopWithoutInsertOrRoute() {
        SessionAdHocFishingStop open = openStop("client-1");
        when(stopRepository.findByFishingSessionIdAndClientEventId(SESSION_ID, "client-2"))
                .thenReturn(Optional.empty());
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(open));

        SessionAdHocFishingStop result = service.start(activeSession(), request("client-2"));

        assertThat(result).isSameAs(open);
        verify(stopRepository, never()).saveAndFlush(any());
        verify(sessionEventWriter, never()).onAdHocStarted(any(), any(), any());
    }

    @Test
    void gisMatchFailureStillStartsStop() {
        when(stopRepository.findByFishingSessionIdAndClientEventId(SESSION_ID, "client-1"))
                .thenReturn(Optional.empty());
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.empty());
        when(locationPointRepository.findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(Optional.of(gps()));
        when(fishingTargetRepository.findNearby(anyDouble(), anyDouble(), anyInt()))
                .thenThrow(new RuntimeException("postgis down"));
        when(stopRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionAdHocFishingStop result = service.start(activeSession(), request("client-1"));

        assertThat(result.isOpen()).isTrue();
        assertThat(result.getFishingTargetId()).isNull();
        verify(sessionEventWriter).onAdHocStarted(any(), any(), any());
    }

    @Test
    void terminalSessionCannotStart() {
        FishingSession session = activeSession();
        session.setStatus(FishingSessionStatus.COMPLETED);
        assertThatThrownBy(() -> service.start(session, request("client-1")))
                .isInstanceOf(BadRequestException.class);
        verify(stopRepository, never()).saveAndFlush(any());
    }

    @Test
    void pausedSessionCannotStart() {
        FishingSession session = activeSession();
        session.setStatus(FishingSessionStatus.PAUSED);
        assertThatThrownBy(() -> service.start(session, request("client-1")))
                .isInstanceOf(BadRequestException.class);
        verify(stopRepository, never()).saveAndFlush(any());
        verify(sessionEventWriter, never()).onAdHocStarted(any(), any(), any());
    }

    @Test
    void startPersistsStopThenRoutesOnceWithoutFakeTripWaypoint() {
        stubFreshStart();
        when(fishingTargetRepository.findNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of());
        when(stopRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionAdHocFishingStop result = service.start(activeSession(), request("client-1"));

        assertThat(result.isOpen()).isTrue();
        assertThat(result.getFishingTargetId()).isNull();
        InOrder order = inOrder(stopRepository, activityStateUpdater, sessionEventWriter);
        order.verify(stopRepository).saveAndFlush(any());
        order.verify(activityStateUpdater).refresh(any(), any());
        order.verify(sessionEventWriter).onAdHocStarted(any(), any(), any());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(sessionEventWriter).onAdHocStarted(any(), any(), payload.capture());
        assertThat(payload.getValue()).containsEntry("adHocFishingStopId", result.getId().toString());
        assertThat(payload.getValue()).doesNotContainKey("tripWaypointId");
        verify(sessionEventWriter, never()).onAdHocEnded(any(), any(), any(), any(), any());
    }

    @Test
    void gisMatchCopiesPhysicalIdsWithoutCreatingTripWaypoint() {
        UUID targetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa11");
        UUID featureId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa12");
        UUID zoneId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa13");
        stubFreshStart();
        LakeFishingTarget target = new LakeFishingTarget();
        target.setId(targetId);
        target.setRepresentativePoint(point());
        target.setSourceFeatureIds(List.of(featureId));
        when(fishingTargetRepository.findNearby(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(target));
        LakeFishingZoneMember member = new LakeFishingZoneMember();
        member.setZoneId(zoneId);
        member.setFishingTargetId(targetId);
        member.setSequence(1);
        when(zoneMemberRepository.findByFishingTargetIdOrderBySequenceAsc(targetId)).thenReturn(List.of(member));
        when(stopRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SessionAdHocFishingStop result = service.start(activeSession(), request("client-1"));

        assertThat(result.getFishingTargetId()).isEqualTo(targetId);
        assertThat(result.getLakeFeatureId()).isEqualTo(featureId);
        assertThat(result.getZoneId()).isEqualTo(zoneId);
        assertThat(result.getFishingTargetId()).isNotEqualTo(SESSION_ID);
    }

    @Test
    void explicitEndClosesStopAndRoutesEnded() {
        SessionAdHocFishingStop open = openStop("client-1");
        when(stopRepository.findByFishingSessionIdAndClientEventId(SESSION_ID, "end-1"))
                .thenReturn(Optional.empty());
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(open));

        Optional<SessionAdHocFishingStop> ended = service.end(activeSession(), request("end-1"));

        assertThat(ended).contains(open);
        assertThat(open.getEndedAt()).isEqualTo(NOW);
        verify(activityStateUpdater).refresh(any(), eq(NOW));
        verify(sessionEventWriter).onAdHocEnded(any(), eq(NOW), eq("end-1"), any(), any());
    }

    @Test
    void leaveXEndsStopAfterDepartureHysteresis() {
        SessionAdHocFishingStop open = openStop("client-1");
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(open));
        List<SessionLocationPoint> trail = List.of(
                gpsAt(NOW.minusSeconds(40), 44.75, -79.35),
                gpsAt(NOW.minusSeconds(30), 44.752, -79.35),
                gpsAt(NOW.minusSeconds(20), 44.752, -79.35),
                gpsAt(NOW.minusSeconds(10), 44.752, -79.35),
                gpsAt(NOW, 44.752, -79.35)
        );

        service.maybeEndFromDeparture(activeSession(), trail);

        assertThat(open.getEndedAt()).isEqualTo(NOW);
        verify(sessionEventWriter).onAdHocEnded(any(), eq(NOW), eq("ad-hoc-end:" + open.getId()), any(), any());
    }

    @Test
    void stillInsideRadiusDoesNotEndStop() {
        SessionAdHocFishingStop open = openStop("client-1");
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(open));
        List<SessionLocationPoint> trail = List.of(
                gpsAt(NOW.minusSeconds(30), 44.75, -79.35),
                gpsAt(NOW.minusSeconds(20), 44.7502, -79.35),
                gpsAt(NOW.minusSeconds(10), 44.7502, -79.35),
                gpsAt(NOW, 44.7502, -79.35)
        );

        service.maybeEndFromDeparture(activeSession(), trail);

        assertThat(open.isOpen()).isTrue();
        verify(sessionEventWriter, never()).onAdHocEnded(any(), any(), any(), any(), any());
    }

    @Test
    void trailingOutsideRequiresConfirmSamplesAndSeconds() {
        List<SessionLocationPoint> outside = List.of(
                gpsAt(NOW.minusSeconds(30), 44.752, -79.35),
                gpsAt(NOW.minusSeconds(20), 44.752, -79.35),
                gpsAt(NOW.minusSeconds(10), 44.752, -79.35),
                gpsAt(NOW, 44.752, -79.35)
        );
        assertThat(AdHocFishingService.departureConfirmed(outside, 30, 4)).isTrue();
        assertThat(AdHocFishingService.departureConfirmed(outside.subList(1, 4), 30, 4)).isFalse();
        assertThat(AdHocFishingService.trailingOutside(
                List.of(gpsAt(NOW.minusSeconds(10), 44.75, -79.35), gpsAt(NOW, 44.752, -79.35)),
                point(),
                100
        )).hasSize(1);
    }

    @Test
    void closeQuietlyAuditsEndedWithoutRoutingAgent() {
        SessionAdHocFishingStop open = openStop("client-1");
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(open));
        FishingSession session = activeSession();
        session.setStatus(FishingSessionStatus.COMPLETED);

        service.closeQuietly(session, NOW);

        assertThat(open.getEndedAt()).isEqualTo(NOW);
        verify(activityStateUpdater).refresh(session, NOW);
        verify(sessionEventWriter).auditAdHocEnded(eq(session), eq(NOW), eq("ad-hoc-quiet-end:" + open.getId()), any());
        verify(sessionEventWriter, never()).onAdHocEnded(any(), any(), any(), any(), any());
    }

    @Test
    void dismissStationaryPromptDoesNotEnqueueGuidance() {
        FishingSession session = activeSession();
        service.dismissStationaryPrompt(session, NOW, gps());
        assertThat(session.getSummary()).containsKeys(
                StationaryFishingDetector.DISMISSED_AT,
                StationaryFishingDetector.DISMISSED_LAT,
                StationaryFishingDetector.DISMISSED_LNG
        );
        verify(sessionEventWriter, never()).onAdHocStarted(any(), any(), any());
        verify(sessionEventWriter, never()).onAdHocEnded(any(), any(), any(), any(), any());
        verify(sessionEventWriter, never()).auditAdHocEnded(any(), any(), any(), any());
    }

    private static FishingSession activeSession() {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setStatus(FishingSessionStatus.ACTIVE);
        session.setStartedAt(NOW.minusSeconds(600));
        return session;
    }

    private static ClientEventRequest request(String clientEventId) {
        return new ClientEventRequest(clientEventId, NOW);
    }

    private static SessionAdHocFishingStop openStop(String clientEventId) {
        SessionAdHocFishingStop stop = new SessionAdHocFishingStop();
        stop.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        stop.setFishingSessionId(SESSION_ID);
        stop.setClientEventId(clientEventId);
        stop.setStartedAt(NOW.minusSeconds(30));
        stop.setLocation(point());
        return stop;
    }

    private void stubFreshStart() {
        when(stopRepository.findByFishingSessionIdAndClientEventId(SESSION_ID, "client-1"))
                .thenReturn(Optional.empty());
        when(stopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.empty());
        when(locationPointRepository.findFirstByFishingSessionIdAndQualityOrderByRecordedAtDesc(
                SESSION_ID, LocationQuality.ACCEPTED)).thenReturn(Optional.of(gps()));
    }

    private static SessionLocationPoint gps() {
        return gpsAt(NOW.minusSeconds(5), 44.75, -79.35);
    }

    private static SessionLocationPoint gpsAt(Instant recordedAt, double lat, double lng) {
        SessionLocationPoint point = new SessionLocationPoint();
        point.setFishingSessionId(SESSION_ID);
        Point geometry = FACTORY.createPoint(new Coordinate(lng, lat));
        geometry.setSRID(GeoMapper.SRID);
        point.setLocation(geometry);
        point.setQuality(LocationQuality.ACCEPTED);
        point.setRecordedAt(recordedAt);
        point.setAccuracyM(BigDecimal.valueOf(8));
        return point;
    }

    private static Point point() {
        Point geometry = FACTORY.createPoint(new Coordinate(-79.35, 44.75));
        geometry.setSRID(GeoMapper.SRID);
        return geometry;
    }
}
