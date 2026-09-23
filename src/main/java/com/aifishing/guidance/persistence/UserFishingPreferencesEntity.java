package com.aifishing.guidance.persistence;

import com.aifishing.common.enums.TechniqueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_fishing_preferences")
public class UserFishingPreferencesEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "schema_version", nullable = false, length = 64)
    private String schemaVersion;

    @Column(name = "avoid_long_move_in_wind")
    private Boolean avoidLongMoveInWind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_techniques", nullable = false, columnDefinition = "jsonb")
    private List<TechniqueType> preferredTechniques = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disliked_techniques", nullable = false, columnDefinition = "jsonb")
    private List<TechniqueType> dislikedTechniques = List.of();

    @Column(name = "max_move_meters", precision = 12, scale = 2)
    private BigDecimal maxMoveMeters;

    @Column(name = "wind_conservatism", precision = 4, scale = 3)
    private BigDecimal windConservatism;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Boolean getAvoidLongMoveInWind() {
        return avoidLongMoveInWind;
    }

    public void setAvoidLongMoveInWind(Boolean avoidLongMoveInWind) {
        this.avoidLongMoveInWind = avoidLongMoveInWind;
    }

    public List<TechniqueType> getPreferredTechniques() {
        return preferredTechniques;
    }

    public void setPreferredTechniques(List<TechniqueType> preferredTechniques) {
        this.preferredTechniques = preferredTechniques == null ? List.of() : preferredTechniques;
    }

    public List<TechniqueType> getDislikedTechniques() {
        return dislikedTechniques;
    }

    public void setDislikedTechniques(List<TechniqueType> dislikedTechniques) {
        this.dislikedTechniques = dislikedTechniques == null ? List.of() : dislikedTechniques;
    }

    public BigDecimal getMaxMoveMeters() {
        return maxMoveMeters;
    }

    public void setMaxMoveMeters(BigDecimal maxMoveMeters) {
        this.maxMoveMeters = maxMoveMeters;
    }

    public BigDecimal getWindConservatism() {
        return windConservatism;
    }

    public void setWindConservatism(BigDecimal windConservatism) {
        this.windConservatism = windConservatism;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
