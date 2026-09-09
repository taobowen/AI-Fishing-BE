package com.aifishing.lake.ingestion.service;

import com.aifishing.lake.ingestion.domain.RawDataObject;
import com.aifishing.lake.ingestion.dto.DatasetType;
import com.aifishing.lake.ingestion.dto.RawPage;
import com.aifishing.lake.ingestion.processor.GeoJsonFeatureParser;
import com.aifishing.lake.ingestion.repo.RawDataObjectRepository;
import com.aifishing.lake.ingestion.storage.RawDataStorage;
import com.aifishing.lake.ingestion.storage.RawObjectKey;
import com.aifishing.lake.ingestion.storage.StoredRawObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RawPagePersistenceService {

    private final RawDataStorage rawDataStorage;
    private final RawDataObjectRepository rawDataObjectRepository;
    private final ObjectMapper objectMapper;
    private final GeoJsonFeatureParser parser;

    public RawPagePersistenceService(
            RawDataStorage rawDataStorage,
            RawDataObjectRepository rawDataObjectRepository,
            ObjectMapper objectMapper,
            GeoJsonFeatureParser parser
    ) {
        this.rawDataStorage = rawDataStorage;
        this.rawDataObjectRepository = rawDataObjectRepository;
        this.objectMapper = objectMapper;
        this.parser = parser;
    }

    @Transactional
    public void persistPages(UUID lakeId, DatasetType type, String provider, String importVersion, List<RawPage> pages) {
        List<Map<String, Object>> manifestPages = new ArrayList<>();
        for (RawPage page : pages) {
            String extension = extensionFor(page.contentType());
            Map<String, Object> metadata = enrichMetadata(page);
            RawObjectKey key = RawObjectKey.page(lakeId.toString(), type.name(), importVersion, page.pageIndex(), extension);
            StoredRawObject stored = rawDataStorage.put(key, page.body(), page.contentType(), metadata);
            RawDataObject row = new RawDataObject();
            row.setLakeId(lakeId);
            row.setDatasetType(type.name());
            row.setImportVersion(importVersion);
            row.setPageIndex(page.pageIndex());
            row.setProvider(provider);
            row.setSourceUrl(page.sourceUrl());
            row.setRequestMetadata(metadata);
            row.setRetrievedAt(Instant.now());
            row.setChecksumSha256(stored.checksumSha256());
            row.setContentType(page.contentType());
            row.setStorageUri(stored.storageUri());
            row.setHttpStatus(page.httpStatus());
            rawDataObjectRepository.save(row);

            Map<String, Object> pageMeta = new LinkedHashMap<>();
            pageMeta.put("pageIndex", page.pageIndex());
            pageMeta.put("sourceUrl", page.sourceUrl());
            pageMeta.put("requestMetadata", metadata);
            pageMeta.put("httpStatus", page.httpStatus());
            pageMeta.put("checksumSha256", stored.checksumSha256());
            pageMeta.put("storageUri", stored.storageUri());
            pageMeta.put("contentType", page.contentType());
            manifestPages.add(pageMeta);
        }
        try {
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("lakeId", lakeId.toString());
            manifest.put("datasetType", type.name());
            manifest.put("provider", provider);
            manifest.put("importVersion", importVersion);
            manifest.put("pageCount", pages.size());
            manifest.put("retrievedAt", Instant.now().toString());
            manifest.put("pages", manifestPages);
            byte[] body = objectMapper.writeValueAsBytes(manifest);
            rawDataStorage.put(
                    RawObjectKey.manifest(lakeId.toString(), type.name(), importVersion),
                    body,
                    "application/json",
                    Map.of("kind", "manifest")
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write raw manifest", ex);
        }
    }

    private Map<String, Object> enrichMetadata(RawPage page) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (page.requestMetadata() != null) {
            metadata.putAll(page.requestMetadata());
        }
        if (!metadata.containsKey("featureCount")) {
            try {
                metadata.put("featureCount", parser.parse(page.body()).size());
            } catch (Exception ex) {
                metadata.put("featureCount", 0);
            }
        }
        if (!metadata.containsKey("exceededTransferLimit")) {
            metadata.put("exceededTransferLimit", parser.exceededTransferLimit(page.body()));
        }
        return metadata;
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return "bin";
        }
        String lower = contentType.toLowerCase();
        if (lower.contains("geojson") || lower.contains("json")) {
            return "geojson";
        }
        if (lower.contains("html")) {
            return "html";
        }
        if (lower.contains("spreadsheet") || lower.contains("excel") || lower.contains("xlsx")) {
            return "xlsx";
        }
        if (lower.contains("text")) {
            return "txt";
        }
        return "bin";
    }
}
