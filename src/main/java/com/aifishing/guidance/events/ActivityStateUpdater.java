package com.aifishing.guidance.events;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import com.aifishing.fishingsession.domain.SessionWaypointProgress;
import com.aifishing.fishingsession.domain.WaypointProgressStatus;
import com.aifishing.fishingsession.repo.SessionAdHocFishingStopRepository;
import com.aifishing.fishingsession.repo.SessionWaypointProgressRepository;
import com.aifishing.fishingsession.service.SessionMapper;
import com.aifishing.guidance.contracts.ActivityStateSource;
import com.aifishing.guidance.contracts.FishingActivityState;
import com.aifishing.guidance.horizon.ActiveGuidanceTarget;
import com.aifishing.guidance.spi.LiveWaypointActivityStore;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * override → session PAUSED → open USER_AD_HOC stop → effort/progress → UNKNOWN.
 * Never treats UNKNOWN as TRANSIT. Stillness is not FISHING.
 */
@Component
public class ActivityStateUpdater {

    private final SessionWaypointProgressRepository progressRepository;
    private final SessionAdHocFishingStopRepository adHocStopRepository;
    private final LiveWaypointActivityStore livePositionStore;
    private final ActiveGuidanceTarget activeGuidanceTarget;

    public ActivityStateUpdater(
            SessionWaypointProgressRepository progressRepository,
            SessionAdHocFishingStopRepository adHocStopRepository,
            LiveWaypointActivityStore livePositionStore,
            ActiveGuidanceTarget activeGuidanceTarget
    ) {
        this.progressRepository = progressRepository;
        this.adHocStopRepository = adHocStopRepository;
        this.livePositionStore = livePositionStore;
        this.activeGuidanceTarget = activeGuidanceTarget;
    }

    public void refresh(FishingSession session, Instant at) {
        if (session == null) {
            return;
        }
        List<SessionWaypointProgress> progress =
                progressRepository.findByFishingSessionIdOrderBySequenceAsc(session.getId());
        refresh(session, progress, at);
    }

    public void refresh(FishingSession session, List<SessionWaypointProgress> progress, Instant at) {
        if (session == null) {
            return;
        }
        boolean adHocOpen = hasOpenAdHoc(session);
        SessionWaypointProgress guidance = adHocOpen ? null : guidanceTargetProgress(session, progress);
        Resolved resolved = resolve(session, progress, adHocOpen, guidance);
        if (session.getActivityState() == resolved.state()
                && session.getActivityStateSource() == resolved.source()) {
            if (session.getActivityStateSince() == null) {
                session.setActivityStateSince(at != null ? at : session.getStartedAt());
            }
            return;
        }
        session.setActivityState(resolved.state());
        session.setActivityStateSource(resolved.source());
        session.setActivityStateSince(at != null ? at : Instant.now());
        livePositionStore.upsertActivityState(session.getId(), resolved.state());
    }

    static Resolved resolve(FishingSession session, List<SessionWaypointProgress> progress) {
        return resolve(session, progress, false, null);
    }

    static Resolved resolve(FishingSession session, List<SessionWaypointProgress> progress, boolean adHocOpen) {
        return resolve(session, progress, adHocOpen, null);
    }

    static Resolved resolve(
            FishingSession session,
            List<SessionWaypointProgress> progress,
            boolean adHocOpen,
            SessionWaypointProgress guidanceTarget
    ) {
        if (session.getActivityStateOverride() != null
                && session.getActivityStateOverride() != FishingActivityState.UNKNOWN) {
            return new Resolved(session.getActivityStateOverride(), ActivityStateSource.OVERRIDE);
        }
        if (session.getStatus() == FishingSessionStatus.PAUSED) {
            return new Resolved(FishingActivityState.PAUSED, ActivityStateSource.SESSION_PAUSE);
        }
        if (adHocOpen) {
            return new Resolved(FishingActivityState.FISHING, ActivityStateSource.USER_AD_HOC);
        }
        if (guidanceTarget != null && (guidanceTarget.getStatus() == WaypointProgressStatus.FISHING
                || guidanceTarget.getStatus() == WaypointProgressStatus.ARRIVED)) {
            return new Resolved(FishingActivityState.FISHING, ActivityStateSource.PROGRESS);
        }
        SessionWaypointProgress current = SessionMapper.currentWaypoint(progress);
        if (current != null && current.getStatus() == WaypointProgressStatus.FISHING) {
            return new Resolved(FishingActivityState.FISHING, ActivityStateSource.PROGRESS);
        }
        if (current != null && current.getStatus() == WaypointProgressStatus.NAVIGATING) {
            return new Resolved(FishingActivityState.TRANSIT, ActivityStateSource.PROGRESS);
        }
        return new Resolved(FishingActivityState.UNKNOWN, ActivityStateSource.UNKNOWN);
    }

    private SessionWaypointProgress guidanceTargetProgress(
            FishingSession session,
            List<SessionWaypointProgress> progress
    ) {
        if (activeGuidanceTarget == null || session == null) {
            return null;
        }
        return SessionMapper.progressForWaypoint(progress, activeGuidanceTarget.resolve(session));
    }

    private boolean hasOpenAdHoc(FishingSession session) {
        if (adHocStopRepository == null || session.getId() == null) {
            return false;
        }
        return adHocStopRepository.findFirstByFishingSessionIdAndEndedAtIsNull(session.getId()).isPresent();
    }

    record Resolved(FishingActivityState state, ActivityStateSource source) {
    }
}
