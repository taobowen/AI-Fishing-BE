package com.aifishing.feedback.catchlog.dto;

import com.aifishing.feedback.catchlog.domain.CatchPhotoStatus;

import java.time.Instant;
import java.util.UUID;

public record CatchPhotoResponse(
        UUID id,
        UUID catchEventId,
        CatchPhotoStatus status,
        String contentType,
        Long sizeBytes,
        String getUrl,
        Instant createdAt,
        Instant completedAt
) {
}
