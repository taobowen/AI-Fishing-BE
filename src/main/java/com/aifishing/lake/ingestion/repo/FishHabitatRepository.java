package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.FishHabitat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishHabitatRepository extends JpaRepository<FishHabitat, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<FishHabitat> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
