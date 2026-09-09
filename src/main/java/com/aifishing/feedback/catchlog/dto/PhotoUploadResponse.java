package com.aifishing.feedback.catchlog.dto;

import java.util.UUID;

public record PhotoUploadResponse(
        UUID photoId,
        String putUrl
) {
}
