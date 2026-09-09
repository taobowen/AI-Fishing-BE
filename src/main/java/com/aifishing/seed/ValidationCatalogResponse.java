package com.aifishing.seed;

import java.util.List;
import java.util.UUID;

public record ValidationCatalogResponse(
        List<LakeRef> lakes
) {
    public record LakeRef(
            UUID id,
            String name,
            boolean created,
            boolean identityResolved
    ) {
    }
}
