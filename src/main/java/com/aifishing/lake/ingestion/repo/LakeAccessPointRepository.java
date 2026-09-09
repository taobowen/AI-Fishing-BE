package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.LakeAccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeAccessPointRepository extends JpaRepository<LakeAccessPoint, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<LakeAccessPoint> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
