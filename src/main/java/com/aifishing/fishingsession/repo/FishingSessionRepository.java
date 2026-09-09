package com.aifishing.fishingsession.repo;

import com.aifishing.common.enums.FishingSessionStatus;
import com.aifishing.fishingsession.domain.FishingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FishingSessionRepository extends JpaRepository<FishingSession, UUID> {

    List<FishingSession> findByTripIdAndUserId(UUID tripId, UUID userId);

    Optional<FishingSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<FishingSession> findFirstByUserIdAndStatusIn(UUID userId, Collection<FishingSessionStatus> statuses);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<FishingSessionStatus> statuses);
}
