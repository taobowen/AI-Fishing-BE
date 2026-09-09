package com.aifishing.gear.repo;

import com.aifishing.gear.domain.Gear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GearRepository extends JpaRepository<Gear, UUID> {

    List<Gear> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Gear> findByIdAndUserId(UUID id, UUID userId);
}
