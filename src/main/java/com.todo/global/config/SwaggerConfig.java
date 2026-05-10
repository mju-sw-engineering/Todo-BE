package com.todo.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(@Value("${app.api-server-url:}") String apiServerUrl) {
        String jwtScheme = "bearerAuth";

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Todo API")
                        .description("Todo 애플리케이션 API 문서")
                        .version("v1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(jwtScheme))
                .components(new Components()
                        .addSecuritySchemes(jwtScheme, new SecurityScheme()
                                .name(jwtScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));

        if (StringUtils.hasText(apiServerUrl)) {
            openAPI.servers(List.of(new Server().url(apiServerUrl)));
        }

        return openAPI;
    }
}
