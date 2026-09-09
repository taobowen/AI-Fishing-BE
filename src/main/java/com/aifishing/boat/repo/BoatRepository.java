package com.aifishing.boat.repo;

import com.aifishing.boat.domain.Boat;
import com.aifishing.common.enums.BoatProvenance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoatRepository extends JpaRepository<Boat, UUID> {

    List<Boat> findByUserIdOrderByNameAsc(UUID userId);

    List<Boat> findByUserIdAndActiveTrueOrderByNameAsc(UUID userId);

    List<Boat> findByUserIdAndActiveTrueAndSystemGeneratedFalseOrderByNameAsc(UUID userId);

    Optional<Boat> findByIdAndUserId(UUID id, UUID userId);

    Optional<Boat> findByIdAndUserIdAndActiveTrue(UUID id, UUID userId);

    Optional<Boat> findByUserIdAndActiveTrueAndSystemGeneratedTrueAndProvenance(UUID userId, BoatProvenance provenance);
}
