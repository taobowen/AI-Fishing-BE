package com.aifishing.guidance.persistence;

import com.aifishing.guidance.contracts.SemanticMemoryKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "semantic_memories")
public class SemanticMemoryEntity extends GuidanceCreatedEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SemanticMemoryKind kind;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "embedding_ref", length = 256)
    private String embeddingRef;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public SemanticMemoryKind getKind() {
        return kind;
    }

    public void setKind(SemanticMemoryKind kind) {
        this.kind = kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getEmbeddingRef() {
        return embeddingRef;
    }

    public void setEmbeddingRef(String embeddingRef) {
        this.embeddingRef = embeddingRef;
    }
}
