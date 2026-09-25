package com.aifishing.fishingtemplate.repo;

import com.aifishing.fishingtemplate.domain.FishingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FishingTemplateRepository extends JpaRepository<FishingTemplate, UUID> {

    List<FishingTemplate> findByUserIdAndLakeIdOrderByNameAsc(UUID userId, UUID lakeId);

    Optional<FishingTemplate> findByIdAndUserId(UUID id, UUID userId);

    Optional<FishingTemplate> findByIdAndUserIdAndLakeId(UUID id, UUID userId, UUID lakeId);
}
