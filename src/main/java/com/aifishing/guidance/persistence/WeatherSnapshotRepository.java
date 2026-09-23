package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeatherSnapshotRepository extends JpaRepository<WeatherSnapshotEntity, UUID> {

    Optional<WeatherSnapshotEntity> findFirstByFishingSessionIdOrderByObservedAtDesc(UUID fishingSessionId);

    List<WeatherSnapshotEntity> findByFishingSessionIdOrderByObservedAtAsc(UUID fishingSessionId);
}
