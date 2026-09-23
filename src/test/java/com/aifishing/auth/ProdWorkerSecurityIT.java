package com.aifishing.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"prod", "worker"})
class ProdWorkerSecurityIT {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres"));

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.auth.issuer", () -> TestJwts.ISSUER);
        registry.add("app.auth.client-id", () -> TestJwts.CLIENT_ID);
        registry.add("app.auth.web-client-id", () -> TestJwts.WEB_CLIENT_ID);
        registry.add("app.raw.storage", () -> "local");
        registry.add("app.raw.local-dir", () -> "./target/test-raw-worker");
        registry.add("app.s3.bucket", () -> "test-bucket");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.admin.enabled", () -> "false");
        registry.add("app.ops.jobs.launcher", () -> "inline");
        registry.add("app.guidance.runtime-mode", () -> "DETERMINISTIC");
    }

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void prodWorkerWebNoneStartsWithoutServletSecurityFilterChain() {
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(DevAuthenticationFilter.class)).isEmpty();
    }
}
