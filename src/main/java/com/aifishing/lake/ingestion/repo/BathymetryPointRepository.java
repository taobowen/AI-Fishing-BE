package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.BathymetryPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BathymetryPointRepository extends JpaRepository<BathymetryPoint, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<BathymetryPoint> findByLakeId(UUID lakeId);

    long countByLakeId(UUID lakeId);

    Optional<BathymetryPoint> findFirstByLakeIdOrderByIdAsc(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
