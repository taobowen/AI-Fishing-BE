package com.aifishing.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CognitoEmailTemplateTest {

    @Test
    void codeTemplateContainsPlaceholderAndProductSubject() throws Exception {
        Path path = Path.of("infra/lib/cognito-email.ts");
        String text = Files.readString(path);
        assertThat(text).contains("Verify your email for AnglerPilot");
        assertThat(text).contains("{####}");
        assertThat(text).contains("Welcome to AnglerPilot");
    }
}
