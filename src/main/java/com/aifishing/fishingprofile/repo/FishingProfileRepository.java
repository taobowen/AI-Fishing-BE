package com.aifishing.fishingprofile.repo;

import com.aifishing.fishingprofile.domain.FishingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FishingProfileRepository extends JpaRepository<FishingProfile, UUID> {

    Optional<FishingProfile> findByUserId(UUID userId);
}
