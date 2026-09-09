package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.RawDataObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RawDataObjectRepository extends JpaRepository<RawDataObject, UUID> {

    List<RawDataObject> findByLakeIdAndDatasetTypeAndImportVersionOrderByPageIndexAsc(
            UUID lakeId,
            String datasetType,
            String importVersion
    );

    List<RawDataObject> findByLakeIdAndDatasetTypeOrderByPageIndexAsc(UUID lakeId, String datasetType);

    long countByLakeIdAndDatasetType(UUID lakeId, String datasetType);

    void deleteByLakeId(UUID lakeId);
}
