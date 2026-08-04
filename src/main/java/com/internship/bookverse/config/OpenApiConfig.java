package com.internship.bookverse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookVerseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BookVerse API")
                        .description("E-Book Management System — CRUD, cover image processing, full-text search, bulk import")
                        .version("1.0.0"));
    }
}
