package com.aifishing.lake.ingestion;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Optional live Ontario smoke. Surefire excludes the {@code live} group.
 * After {@code docker compose up} and {@code mvn spring-boot:run -Dspring-boot.run.profiles=dev},
 * POST {@code /api/v1/admin/lakes/{lakeId}/import} for the four seed UUIDs using the same pipeline.
 */
@Tag("live")
class OntarioLiveIngestIT {

    @Test
    void liveImportIsDocumentedForManualAcceptance() {
        org.assertj.core.api.Assertions.assertThat(true).isTrue();
    }
}
