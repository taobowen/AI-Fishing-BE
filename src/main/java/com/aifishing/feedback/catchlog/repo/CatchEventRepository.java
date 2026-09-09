package com.aifishing.feedback.catchlog.repo;

import com.aifishing.feedback.catchlog.domain.CatchEvent;
import com.aifishing.feedback.catchlog.domain.CatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatchEventRepository extends JpaRepository<CatchEvent, UUID> {

    Optional<CatchEvent> findByFishingSessionIdAndClientCatchId(UUID fishingSessionId, String clientCatchId);

    List<CatchEvent> findByFishingSessionIdOrderByOccurredAtAsc(UUID fishingSessionId);

    List<CatchEvent> findByFishingSessionIdAndStatusOrderByOccurredAtAsc(UUID fishingSessionId, CatchStatus status);

    Optional<CatchEvent> findByIdAndUserId(UUID id, UUID userId);
}
