package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.LakeFishSpecies;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeFishSpeciesRepository extends JpaRepository<LakeFishSpecies, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<LakeFishSpecies> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
