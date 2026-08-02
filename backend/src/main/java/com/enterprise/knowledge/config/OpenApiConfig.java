package com.enterprise.knowledge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger documentation configuration.
 * Accessible at /swagger-ui.html and /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Enterprise AI Knowledge Assistant API")
                .version("1.0.0")
                .description("Production-grade RAG-powered knowledge assistant API with semantic search, " +
                    "chat interface, and document management capabilities.")
                .contact(new Contact()
                    .name("Platform Engineering")
                    .email("platform@enterprise.com")))
            .servers(List.of(
                new Server().url("http://localhost:8080/api/v1").description("Local"),
                new Server().url("https://api.knowledge.enterprise.com/v1").description("Production")
            ))
            .components(new Components()
                .addSecuritySchemes("Bearer", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token authentication")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
