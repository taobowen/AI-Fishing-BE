package com.aifishing.feedback.catchlog.dto;

import jakarta.validation.constraints.NotBlank;

public record PhotoUploadRequest(
        @NotBlank String contentType
) {
}
