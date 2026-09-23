package com.aifishing.user.api;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRequest(
        @Size(min = 1, max = 255) String displayName,
        List<String> ownedLureFamilies,
        Boolean kitSetupComplete
) {
}
