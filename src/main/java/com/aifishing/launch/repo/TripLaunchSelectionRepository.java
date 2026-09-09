package com.aifishing.launch.repo;

import com.aifishing.launch.domain.TripLaunchSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripLaunchSelectionRepository extends JpaRepository<TripLaunchSelection, UUID> {
}
