package com.aifishing.lake.ingestion.admin;

import com.aifishing.lake.ingestion.domain.RawDataObject;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.repo.RawDataObjectRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PaginationReportAssembler {

    private final RawDataObjectRepository rawDataObjectRepository;

    public PaginationReportAssembler(RawDataObjectRepository rawDataObjectRepository) {
        this.rawDataObjectRepository = rawDataObjectRepository;
    }

    public PaginationReport forDataset(UUID lakeId, DatasetType type) {
        List<RawDataObject> rows = rawDataObjectRepository.findByLakeIdAndDatasetTypeOrderByPageIndexAsc(
                lakeId,
                type.name()
        );
        if (rows.isEmpty()) {
            return PaginationReport.empty();
        }
        String latestVersion = rows.stream()
                .max(Comparator.comparing(row -> row.getRetrievedAt() == null ? Instant.EPOCH : row.getRetrievedAt()))
                .map(RawDataObject::getImportVersion)
                .orElse(null);
        List<RawDataObject> latest = rows.stream()
                .filter(row -> latestVersion != null && latestVersion.equals(row.getImportVersion()))
                .sorted(Comparator.comparingInt(RawDataObject::getPageIndex))
                .toList();
        List<Map<String, Object>> metadata = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        for (RawDataObject row : latest) {
            Map<String, Object> meta = row.getRequestMetadata() == null ? Map.of() : row.getRequestMetadata();
            metadata.add(meta);
            uris.add(row.getStorageUri());
        }
        return PaginationReport.fromPageMetadata(metadata, uris);
    }
}
