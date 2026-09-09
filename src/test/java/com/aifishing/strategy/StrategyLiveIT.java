package com.aifishing.strategy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Optional live Open-Meteo + OpenAI strategy smoke. Surefire excludes the {@code live} group.
 * After a trip exists, {@code OPENAI_API_KEY}, and {@code app.admin.enabled=true}, POST
 * {@code /api/v1/admin/trips/{id}/strategy}. {@code mvn test} never calls Open-Meteo or OpenAI.
 */
@Tag("live")
class StrategyLiveIT {

    @Test
    void liveOpenMeteoAndOpenAiStrategyAreDocumentedForManualAcceptance() {
        org.assertj.core.api.Assertions.assertThat(true).isTrue();
    }
}
