package com.aifishing.user.api;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 255) String displayName
) {
}
