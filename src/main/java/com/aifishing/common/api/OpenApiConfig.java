package com.aifishing.common.api;

import com.aifishing.auth.DevAuthenticationFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Fishing API")
                        .version("v1")
                        .description("Phase 1 backend foundation and CRUD APIs. Authenticate with X-User-Id in development."))
                .addSecurityItem(new SecurityRequirement().addList("userId"))
                .components(new Components().addSecuritySchemes(
                        "userId",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(DevAuthenticationFilter.USER_ID_HEADER)
                ));
    }
}
