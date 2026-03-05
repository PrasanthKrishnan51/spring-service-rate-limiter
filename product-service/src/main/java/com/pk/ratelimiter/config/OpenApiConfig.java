package com.pk.ratelimiter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Product Service API")
                        .description("CRUD microservice with service-level Token Bucket rate limiting via Redis")
                        .version("1.0.0")
                        .contact(new Contact().name("PrasanthKrishnan"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Direct service"),
                        new Server().url("http://localhost:8080").description("Via API Gateway")));
    }
}
