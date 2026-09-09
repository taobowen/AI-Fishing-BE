package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.LakeBoundaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LakeBoundaryRecordRepository extends JpaRepository<LakeBoundaryRecord, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<LakeBoundaryRecord> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
