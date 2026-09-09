package com.aifishing.feedback.effort.repo;

import com.aifishing.feedback.effort.domain.SessionPauseInterval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionPauseIntervalRepository extends JpaRepository<SessionPauseInterval, UUID> {

    List<SessionPauseInterval> findByFishingSessionIdOrderByPausedAtAsc(UUID fishingSessionId);

    Optional<SessionPauseInterval> findFirstByFishingSessionIdAndResumedAtIsNull(UUID fishingSessionId);
}
