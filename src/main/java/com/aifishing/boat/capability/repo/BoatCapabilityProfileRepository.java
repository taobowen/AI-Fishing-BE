package com.aifishing.boat.capability.repo;

import com.aifishing.boat.capability.domain.BoatCapabilityProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BoatCapabilityProfileRepository extends JpaRepository<BoatCapabilityProfileEntity, UUID> {

    Optional<BoatCapabilityProfileEntity> findByConfigurationFingerprint(String configurationFingerprint);
}
