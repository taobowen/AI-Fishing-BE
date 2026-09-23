package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LureEventRepository extends JpaRepository<LureEventEntity, UUID> {

    List<LureEventEntity> findByFishingSessionIdOrderByOccurredAtAsc(UUID fishingSessionId);
}
