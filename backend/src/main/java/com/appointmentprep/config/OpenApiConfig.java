package com.appointmentprep.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. The spec feeds the generated frontend client (src/api/schema.d.ts). */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info().title("Appointment Prep API").version("v1"))
        // Relative and fixed, so the spec doesn't depend on the host or port that served it.
        .addServersItem(new Server().url("/api"));
  }
}
