package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.FishingRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishingRestrictionRepository extends JpaRepository<FishingRestriction, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<FishingRestriction> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
