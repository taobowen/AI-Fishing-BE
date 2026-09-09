package com.aifishing.boat.capability.domain;

import com.aifishing.common.enums.CapabilitySource;
import com.aifishing.common.enums.WindWaveCapability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "boat_capability_profiles")
public class BoatCapabilityProfileEntity {

    @Id
    private UUID id;

    @Column(name = "configuration_fingerprint", nullable = false, unique = true, length = 64)
    private String configurationFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "normalized_configuration", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> normalizedConfiguration = new LinkedHashMap<>();

    @Column(name = "cruise_speed_kmh")
    private BigDecimal cruiseSpeedKmh;

    @Column(name = "cruise_speed_confidence")
    private BigDecimal cruiseSpeedConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "cruise_speed_source", length = 32)
    private CapabilitySource cruiseSpeedSource;

    @Column(name = "practical_range_km")
    private BigDecimal practicalRangeKm;

    @Column(name = "range_confidence")
    private BigDecimal rangeConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "range_source", length = 32)
    private CapabilitySource rangeSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_wave_capability", length = 16)
    private WindWaveCapability windWaveCapability;

    @Column(name = "wind_wave_confidence")
    private BigDecimal windWaveConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_wave_source", length = 32)
    private CapabilitySource windWaveSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> warnings = new ArrayList<>();

    @Column(name = "resolver_version", nullable = false, length = 64)
    private String resolverVersion;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "web_search_used", nullable = false)
    private boolean webSearchUsed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceMetadata = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_resolution_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawResolutionMetadata = new LinkedHashMap<>();

    @Column(name = "resolved_at", nullable = false)
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (resolvedAt == null) {
            resolvedAt = now;
        }
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        if (normalizedConfiguration == null) {
            normalizedConfiguration = new LinkedHashMap<>();
        }
        if (evidenceMetadata == null) {
            evidenceMetadata = new LinkedHashMap<>();
        }
        if (rawResolutionMetadata == null) {
            rawResolutionMetadata = new LinkedHashMap<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getConfigurationFingerprint() {
        return configurationFingerprint;
    }

    public void setConfigurationFingerprint(String configurationFingerprint) {
        this.configurationFingerprint = configurationFingerprint;
    }

    public Map<String, Object> getNormalizedConfiguration() {
        return normalizedConfiguration;
    }

    public void setNormalizedConfiguration(Map<String, Object> normalizedConfiguration) {
        this.normalizedConfiguration = normalizedConfiguration == null ? new LinkedHashMap<>() : normalizedConfiguration;
    }

    public BigDecimal getCruiseSpeedKmh() {
        return cruiseSpeedKmh;
    }

    public void setCruiseSpeedKmh(BigDecimal cruiseSpeedKmh) {
        this.cruiseSpeedKmh = cruiseSpeedKmh;
    }

    public BigDecimal getCruiseSpeedConfidence() {
        return cruiseSpeedConfidence;
    }

    public void setCruiseSpeedConfidence(BigDecimal cruiseSpeedConfidence) {
        this.cruiseSpeedConfidence = cruiseSpeedConfidence;
    }

    public CapabilitySource getCruiseSpeedSource() {
        return cruiseSpeedSource;
    }

    public void setCruiseSpeedSource(CapabilitySource cruiseSpeedSource) {
        this.cruiseSpeedSource = cruiseSpeedSource;
    }

    public BigDecimal getPracticalRangeKm() {
        return practicalRangeKm;
    }

    public void setPracticalRangeKm(BigDecimal practicalRangeKm) {
        this.practicalRangeKm = practicalRangeKm;
    }

    public BigDecimal getRangeConfidence() {
        return rangeConfidence;
    }

    public void setRangeConfidence(BigDecimal rangeConfidence) {
        this.rangeConfidence = rangeConfidence;
    }

    public CapabilitySource getRangeSource() {
        return rangeSource;
    }

    public void setRangeSource(CapabilitySource rangeSource) {
        this.rangeSource = rangeSource;
    }

    public WindWaveCapability getWindWaveCapability() {
        return windWaveCapability;
    }

    public void setWindWaveCapability(WindWaveCapability windWaveCapability) {
        this.windWaveCapability = windWaveCapability;
    }

    public BigDecimal getWindWaveConfidence() {
        return windWaveConfidence;
    }

    public void setWindWaveConfidence(BigDecimal windWaveConfidence) {
        this.windWaveConfidence = windWaveConfidence;
    }

    public CapabilitySource getWindWaveSource() {
        return windWaveSource;
    }

    public void setWindWaveSource(CapabilitySource windWaveSource) {
        this.windWaveSource = windWaveSource;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : warnings;
    }

    public String getResolverVersion() {
        return resolverVersion;
    }

    public void setResolverVersion(String resolverVersion) {
        this.resolverVersion = resolverVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public boolean isWebSearchUsed() {
        return webSearchUsed;
    }

    public void setWebSearchUsed(boolean webSearchUsed) {
        this.webSearchUsed = webSearchUsed;
    }

    public Map<String, Object> getEvidenceMetadata() {
        return evidenceMetadata;
    }

    public void setEvidenceMetadata(Map<String, Object> evidenceMetadata) {
        this.evidenceMetadata = evidenceMetadata == null ? new LinkedHashMap<>() : evidenceMetadata;
    }

    public Map<String, Object> getRawResolutionMetadata() {
        return rawResolutionMetadata;
    }

    public void setRawResolutionMetadata(Map<String, Object> rawResolutionMetadata) {
        this.rawResolutionMetadata = rawResolutionMetadata == null ? new LinkedHashMap<>() : rawResolutionMetadata;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
