package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.CompassDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "weather_snapshots")
public class WeatherSnapshotEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> envelope;

    @Column(name = "wind_speed_kph")
    private BigDecimal windSpeedKph;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_direction", length = 8)
    private CompassDirection windDirection;

    @Column(name = "temperature_c")
    private BigDecimal temperatureC;

    @Column(name = "pressure_hpa")
    private BigDecimal pressureHpa;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public Map<String, Object> getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Map<String, Object> envelope) {
        this.envelope = envelope;
    }

    public BigDecimal getWindSpeedKph() {
        return windSpeedKph;
    }

    public void setWindSpeedKph(BigDecimal windSpeedKph) {
        this.windSpeedKph = windSpeedKph;
    }

    public CompassDirection getWindDirection() {
        return windDirection;
    }

    public void setWindDirection(CompassDirection windDirection) {
        this.windDirection = windDirection;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public void setTemperatureC(BigDecimal temperatureC) {
        this.temperatureC = temperatureC;
    }

    public BigDecimal getPressureHpa() {
        return pressureHpa;
    }

    public void setPressureHpa(BigDecimal pressureHpa) {
        this.pressureHpa = pressureHpa;
    }
}
