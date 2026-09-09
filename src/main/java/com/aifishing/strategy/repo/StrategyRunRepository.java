package com.aifishing.strategy.repo;

import com.aifishing.strategy.domain.StrategyRun;
import com.aifishing.strategy.domain.StrategyRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StrategyRunRepository extends JpaRepository<StrategyRun, UUID> {

    List<StrategyRun> findByTripIdOrderByStartedAtDesc(UUID tripId);

    Optional<StrategyRun> findFirstByTripIdAndStatusOrderByCompletedAtDesc(UUID tripId, StrategyRunStatus status);
}
