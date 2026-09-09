package com.aifishing.lake.processing.vision;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Optional live Vision + import smoke. Surefire excludes the {@code live} group.
 * After Phase 2 admin import and {@code OPENAI_API_KEY}, POST
 * {@code /api/v1/admin/lakes/{id}/process?pipeline=VISION} then Hybrid/benchmark.
 * {@code mvn test} never calls OpenAI.
 */
@Tag("live")
class VisionLiveIT {

    @Test
    void liveCanonicalRenderVisionIsDocumentedForManualAcceptance() {
        org.assertj.core.api.Assertions.assertThat(true).isTrue();
    }
}
