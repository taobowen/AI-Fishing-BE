package com.aifishing.guidance.persistence;

import com.aifishing.common.enums.LureFamily;
import com.aifishing.common.enums.PresentationTechnique;
import com.aifishing.guidance.contracts.RetrieveStyle;
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
@Table(name = "lure_events")
public class LureEventEntity extends GuidanceCreatedEntity {

    @Column(name = "fishing_session_id", nullable = false)
    private UUID fishingSessionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lure_family", nullable = false, length = 32)
    private LureFamily lureFamily;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PresentationTechnique presentation;

    @Column(name = "depth_m")
    private BigDecimal depthM;

    @Enumerated(EnumType.STRING)
    @Column(name = "retrieve_style", length = 32)
    private RetrieveStyle retrieveStyle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> envelope;

    public UUID getFishingSessionId() {
        return fishingSessionId;
    }

    public void setFishingSessionId(UUID fishingSessionId) {
        this.fishingSessionId = fishingSessionId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LureFamily getLureFamily() {
        return lureFamily;
    }

    public void setLureFamily(LureFamily lureFamily) {
        this.lureFamily = lureFamily;
    }

    public PresentationTechnique getPresentation() {
        return presentation;
    }

    public void setPresentation(PresentationTechnique presentation) {
        this.presentation = presentation;
    }

    public BigDecimal getDepthM() {
        return depthM;
    }

    public void setDepthM(BigDecimal depthM) {
        this.depthM = depthM;
    }

    public RetrieveStyle getRetrieveStyle() {
        return retrieveStyle;
    }

    public void setRetrieveStyle(RetrieveStyle retrieveStyle) {
        this.retrieveStyle = retrieveStyle;
    }

    public Map<String, Object> getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Map<String, Object> envelope) {
        this.envelope = envelope;
    }
}
