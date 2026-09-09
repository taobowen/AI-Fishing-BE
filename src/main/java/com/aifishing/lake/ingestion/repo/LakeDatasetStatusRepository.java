package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.LakeDatasetStatus;
import com.aifishing.lake.ingestion.dto.DatasetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LakeDatasetStatusRepository extends JpaRepository<LakeDatasetStatus, UUID> {

    List<LakeDatasetStatus> findByLakeIdOrderByDatasetTypeAsc(UUID lakeId);

    Optional<LakeDatasetStatus> findByLakeIdAndDatasetTypeAndProvider(UUID lakeId, DatasetType datasetType, String provider);

    void deleteByLakeId(UUID lakeId);
}
