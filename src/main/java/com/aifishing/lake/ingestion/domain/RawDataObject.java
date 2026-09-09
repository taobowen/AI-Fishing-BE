package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "raw_data_objects")
public class RawDataObject {

    @Id
    private UUID id;

    @Column(name = "lake_id", nullable = false)
    private UUID lakeId;

    @Column(name = "dataset_type", nullable = false)
    private String datasetType;

    @Column(name = "import_version", nullable = false)
    private String importVersion;

    @Column(name = "page_index", nullable = false)
    private int pageIndex;

    @Column(nullable = false)
    private String provider;

    @Column(name = "source_url")
    private String sourceUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_metadata", columnDefinition = "jsonb")
    private Map<String, Object> requestMetadata;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_uri", nullable = false)
    private String storageUri;

    @Column(name = "http_status")
    private Integer httpStatus;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLakeId() {
        return lakeId;
    }

    public void setLakeId(UUID lakeId) {
        this.lakeId = lakeId;
    }

    public String getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(String datasetType) {
        this.datasetType = datasetType;
    }

    public String getImportVersion() {
        return importVersion;
    }

    public void setImportVersion(String importVersion) {
        this.importVersion = importVersion;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Map<String, Object> getRequestMetadata() {
        return requestMetadata;
    }

    public void setRequestMetadata(Map<String, Object> requestMetadata) {
        this.requestMetadata = requestMetadata;
    }

    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }
}
