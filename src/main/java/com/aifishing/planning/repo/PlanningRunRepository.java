package com.aifishing.planning.repo;

import com.aifishing.planning.domain.PlanningRun;
import com.aifishing.planning.domain.PlanningRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanningRunRepository extends JpaRepository<PlanningRun, UUID> {

    List<PlanningRun> findByTripIdOrderByStartedAtDesc(UUID tripId);

    Optional<PlanningRun> findFirstByTripIdAndStatusOrderByCompletedAtDesc(UUID tripId, PlanningRunStatus status);
}
