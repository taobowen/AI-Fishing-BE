package com.aifishing.guidance.events;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityStateUpdaterTest {

    private static final Instant AT = Instant.parse("2026-09-16T15:00:00Z");
    private static final UUID SESSION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private SessionWaypointProgressRepository progressRepository;
    @Mock
    private SessionAdHocFishingStopRepository adHocStopRepository;
    @Mock
    private LiveWaypointActivityStore livePositionStore;

    private ActivityStateUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new ActivityStateUpdater(progressRepository, adHocStopRepository, livePositionStore, null);
    }

    @Test
    void pauseProjectsActivityWithoutRequiringANewPoint() {
        FishingSession session = session(FishingSessionStatus.PAUSED, FishingActivityState.FISHING);
        when(progressRepository.findByFishingSessionIdOrderBySequenceAsc(SESSION_ID)).thenReturn(List.of());

        updater.refresh(session, AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.PAUSED);
        verify(livePositionStore).upsertActivityState(SESSION_ID, FishingActivityState.PAUSED);
    }

    @Test
    void fishingProgressProjectsFishing() {
        FishingSession session = session(FishingSessionStatus.ACTIVE, FishingActivityState.TRANSIT);
        SessionWaypointProgress fishing = new SessionWaypointProgress();
        fishing.setStatus(WaypointProgressStatus.FISHING);
        updater.refresh(session, List.of(fishing), AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.FISHING);
        verify(livePositionStore).upsertActivityState(SESSION_ID, FishingActivityState.FISHING);
    }

    @Test
    void unchangedStateDoesNotRewriteProjection() {
        FishingSession session = session(FishingSessionStatus.ACTIVE, FishingActivityState.UNKNOWN);
        session.setActivityStateSource(ActivityStateSource.UNKNOWN);
        updater.refresh(session, List.of(), AT);

        verify(livePositionStore, never()).upsertActivityState(SESSION_ID, FishingActivityState.UNKNOWN);
    }

    @Test
    void openAdHocStopProjectsFishingUserAdHoc() {
        FishingSession session = session(FishingSessionStatus.ACTIVE, FishingActivityState.TRANSIT);
        SessionWaypointProgress navigating = new SessionWaypointProgress();
        navigating.setStatus(WaypointProgressStatus.NAVIGATING);
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(new com.aifishing.fishingsession.domain.SessionAdHocFishingStop()));

        updater.refresh(session, List.of(navigating), AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(session.getActivityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        verify(livePositionStore).upsertActivityState(SESSION_ID, FishingActivityState.FISHING);
    }

    @Test
    void pauseStillWinsOverOpenAdHocStop() {
        FishingSession session = session(FishingSessionStatus.PAUSED, FishingActivityState.FISHING);
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(new com.aifishing.fishingsession.domain.SessionAdHocFishingStop()));

        updater.refresh(session, List.of(), AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.PAUSED);
        assertThat(session.getActivityStateSource()).isEqualTo(ActivityStateSource.SESSION_PAUSE);
    }

    @Test
    void guidanceTargetFishingWinsOverOriginalNavigating() {
        UUID spot2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000002");
        UUID spot3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
        ActiveGuidanceTarget target = org.mockito.Mockito.mock(ActiveGuidanceTarget.class);
        updater = new ActivityStateUpdater(progressRepository, adHocStopRepository, livePositionStore, target);
        FishingSession session = session(FishingSessionStatus.ACTIVE, FishingActivityState.TRANSIT);
        org.mockito.Mockito.when(target.resolve(session)).thenReturn(spot3);
        SessionWaypointProgress navigating = new SessionWaypointProgress();
        navigating.setTripWaypointId(spot2);
        navigating.setSequence(2);
        navigating.setStatus(WaypointProgressStatus.NAVIGATING);
        SessionWaypointProgress fishing = new SessionWaypointProgress();
        fishing.setTripWaypointId(spot3);
        fishing.setSequence(3);
        fishing.setStatus(WaypointProgressStatus.FISHING);

        updater.refresh(session, List.of(navigating, fishing), AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(session.getActivityStateSource()).isEqualTo(ActivityStateSource.PROGRESS);
    }

    @Test
    void openAdHocStillWinsOverGuidanceTargetFishing() {
        UUID spot3 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-000000000003");
        ActiveGuidanceTarget target = org.mockito.Mockito.mock(ActiveGuidanceTarget.class);
        updater = new ActivityStateUpdater(progressRepository, adHocStopRepository, livePositionStore, target);
        FishingSession session = session(FishingSessionStatus.ACTIVE, FishingActivityState.TRANSIT);
        SessionWaypointProgress fishing = new SessionWaypointProgress();
        fishing.setTripWaypointId(spot3);
        fishing.setStatus(WaypointProgressStatus.FISHING);
        when(adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(SESSION_ID))
                .thenReturn(Optional.of(new com.aifishing.fishingsession.domain.SessionAdHocFishingStop()));

        updater.refresh(session, List.of(fishing), AT);

        assertThat(session.getActivityState()).isEqualTo(FishingActivityState.FISHING);
        assertThat(session.getActivityStateSource()).isEqualTo(ActivityStateSource.USER_AD_HOC);
        org.mockito.Mockito.verify(target, org.mockito.Mockito.never()).resolve(session);
    }

    private static FishingSession session(FishingSessionStatus status, FishingActivityState activity) {
        FishingSession session = new FishingSession();
        session.setId(SESSION_ID);
        session.setStatus(status);
        session.setActivityState(activity);
        session.setActivityStateSource(ActivityStateSource.PROGRESS);
        session.setStartedAt(AT.minusSeconds(60));
        return session;
    }
}
