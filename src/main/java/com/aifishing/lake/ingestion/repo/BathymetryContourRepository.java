package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.BathymetryContour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BathymetryContourRepository extends JpaRepository<BathymetryContour, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<BathymetryContour> findByLakeId(UUID lakeId);

    long countByLakeId(UUID lakeId);

    Optional<BathymetryContour> findFirstByLakeIdOrderByIdAsc(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
