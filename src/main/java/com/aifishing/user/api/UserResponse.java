package com.aifishing.user.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        List<String> ownedLureFamilies,
        boolean kitSetupComplete,
        Instant createdAt,
        Instant updatedAt
) {
}
