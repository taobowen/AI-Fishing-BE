package com.aifishing.lake.ingestion.repo;

import com.aifishing.lake.ingestion.domain.FishStockingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FishStockingRecordRepository extends JpaRepository<FishStockingRecord, UUID> {

    void deleteByLakeIdAndProvider(UUID lakeId, String provider);

    List<FishStockingRecord> findByLakeId(UUID lakeId);

    long countByLakeIdAndProvider(UUID lakeId, String provider);
}
