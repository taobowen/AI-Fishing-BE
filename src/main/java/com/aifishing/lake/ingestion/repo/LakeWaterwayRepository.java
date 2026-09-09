package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.LakeWaterway;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeWaterwayRepository extends JpaRepository<LakeWaterway, UUID> {

    void deleteByLakeIdAndProviderAndType(UUID lakeId, String provider, String type);

    List<LakeWaterway> findByLakeId(UUID lakeId);

    List<LakeWaterway> findByLakeIdAndType(UUID lakeId, String type);

    long countByLakeIdAndType(UUID lakeId, String type);

    Optional<LakeWaterway> findFirstByLakeIdAndTypeOrderByIdAsc(UUID lakeId, String type);

    long countByLakeIdAndProviderAndType(UUID lakeId, String provider, String type);
}
