package com.aifishing.guidance.eval;

import com.aifishing.guidance.GuidanceProperties;
import com.aifishing.guidance.contracts.GuidanceContracts;
import com.aifishing.guidance.contracts.UsageTelemetry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UsageTelemetryAssemblerTest {

    @Test
    void missingRawUsageStaysNullNeverNumericZero() {
        UsageTelemetry telemetry = UsageTelemetryAssembler.assemble(
                "openai", "gpt-4o", "gpt-4o-2024", "guidance-prompt-v1",
                Map.of(),
                TokenPricing.none()
        );
        assertThat(telemetry.inputTokens()).isNull();
        assertThat(telemetry.outputTokens()).isNull();
        assertThat(telemetry.totalTokens()).isNull();
        assertThat(telemetry.costUsd()).isNull();
        assertThat(telemetry.hasTokenTelemetry()).isFalse();
        JsonNode node = GuidanceContracts.mapper().valueToTree(telemetry);
        assertThat(node.has("inputTokens")).isFalse();
        assertThat(node.has("outputTokens")).isFalse();
        assertThat(node.has("totalTokens")).isFalse();
        assertThat(node.has("costUsd")).isFalse();
        assertThat(node.path("modelProvider").asText()).isEqualTo("openai");
        assertThat(node.path("modelName").asText()).isEqualTo("gpt-4o");
    }

    @Test
    void costStaysNullWithoutExplicitPricingVersion() {
        UsageTelemetry telemetry = UsageTelemetryAssembler.assemble(
                "openai", "gpt-4o", "v1", "prompt-1",
                Map.of("input_tokens", 100, "output_tokens", 50, "total_tokens", 150),
                new TokenPricing(null, 0.005, 0.015)
        );
        assertThat(telemetry.inputTokens()).isEqualTo(100);
        assertThat(telemetry.outputTokens()).isEqualTo(50);
        assertThat(telemetry.totalTokens()).isEqualTo(150);
        assertThat(telemetry.costUsd()).isNull();
        assertThat(telemetry.pricingVersion()).isNull();
        assertThat(new UsageTelemetry(
                telemetry.schemaVersion(), "openai", "gpt-4o", "v1", "prompt-1",
                100, 50, 150, 1.25, null
        ).costUsd()).isNull();
    }

    @Test
    void costIsDerivedOnlyWithPricingVersionAndKnownTokens() {
        TokenPricing pricing = new TokenPricing("pricing-2026-09", 0.005, 0.015);
        UsageTelemetry priced = UsageTelemetryAssembler.assemble(
                "openai", "gpt-4o", "v1", "prompt-1",
                Map.of("inputTokens", 1000, "outputTokens", 1000),
                pricing
        );
        assertThat(priced.pricingVersion()).isEqualTo("pricing-2026-09");
        assertThat(priced.costUsd()).isEqualTo(0.02);
        assertThat(priced.totalTokens()).isEqualTo(2000);

        UsageTelemetry unknownTokens = UsageTelemetryAssembler.assemble(
                "openai", "gpt-4o", "v1", "prompt-1",
                Map.of("id", "resp-1"),
                pricing
        );
        assertThat(unknownTokens.costUsd()).isNull();
        assertThat(unknownTokens.hasTokenTelemetry()).isFalse();
    }

    @Test
    void propertiesWithoutPricingVersionDoNotInventCost() {
        GuidanceProperties properties = new GuidanceProperties();
        properties.getEval().setInputUsdPer1kTokens(0.01);
        properties.getEval().setOutputUsdPer1kTokens(0.03);
        TokenPricing fromProps = TokenPricing.from(properties);
        assertThat(fromProps.canPrice()).isFalse();
        UsageTelemetry telemetry = UsageTelemetryAssembler.assemble(
                "deterministic", "fixture", "1", "prompt-1",
                map("input_tokens", 10, "output_tokens", 5),
                fromProps
        );
        assertThat(telemetry.costUsd()).isNull();
        assertThat(UsageTelemetryAssembler.copyRaw(map("input_tokens", 10)))
                .containsEntry("input_tokens", 10);
    }

    private static Map<String, Object> map(Object... keyValues) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
