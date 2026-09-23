package com.aifishing.guidance.contracts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceClientTypesDriftTest {

    @Test
    void generatedTypescriptMatchesCanonicalSchema() throws Exception {
        Path scripts = Path.of("scripts").toAbsolutePath();
        run(scripts, "npm", "ci");
        run(scripts, "node", "generate-guidance-client-types.mjs", "--check");
        Path generatedPath = Path.of("../AI-Fishing-FE/src/api/generated/guidance.ts");
        if (java.nio.file.Files.exists(generatedPath)) {
            String generated = java.nio.file.Files.readString(generatedPath);
            assertThat(generated).contains("ONLINE_METRICS_ROLLUP");
            assertThat(generated).doesNotContain("EvalSuiteKind", "EvalRun", "GuidanceSuccessKind", "FrozenAgentRunSnapshot");
        }
    }

    private static void run(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
        assertThat(process.exitValue())
                .withFailMessage(output)
                .isZero();
    }
}
