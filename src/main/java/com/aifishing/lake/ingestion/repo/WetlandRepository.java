package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.Wetland;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WetlandRepository extends JpaRepository<Wetland, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<Wetland> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
