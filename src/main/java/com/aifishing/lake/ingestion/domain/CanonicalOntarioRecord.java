package com.aifishing.lake.ingestion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@MappedSuperclass
public abstract class CanonicalOntarioRecord {

    @Id
    private UUID id;

    @Column(name = "lake_id")
    private UUID lakeId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "source_record_id")
    private String sourceRecordId;

    @Column(name = "import_version", nullable = false)
    private String importVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata", columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadata;

    @Column(nullable = false)
    private String source;

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(String sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public String getImportVersion() {
        return importVersion;
    }

    public void setImportVersion(String importVersion) {
        this.importVersion = importVersion;
    }

    public Map<String, Object> getSourceMetadata() {
        return sourceMetadata;
    }

    public void setSourceMetadata(Map<String, Object> sourceMetadata) {
        this.sourceMetadata = sourceMetadata;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
