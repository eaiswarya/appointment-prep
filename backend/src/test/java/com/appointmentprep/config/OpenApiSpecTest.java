package com.appointmentprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.appointmentprep.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Serves the OpenAPI spec (dev profile) and exports it to {@code build/openapi/openapi.json}, the
 * input for {@code npm run generate:api}. Run {@code ./gradlew exportOpenApi} to refresh it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class OpenApiSpecTest {

  static final Path EXPORT = Path.of("build", "openapi", "openapi.json");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate rest;

  @Autowired private ObjectMapper json;

  @Test
  void servesAndExportsTheSpec() throws Exception {
    ResponseEntity<String> response =
        rest.getForEntity("http://localhost:" + port + "/api/docs/openapi.json", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode spec = json.readTree(response.getBody());
    assertThat(spec.path("openapi").asText()).startsWith("3.");
    // A fixed server URL keeps the spec identical across runs and ports.
    assertThat(spec.path("servers").get(0).path("url").asText()).isEqualTo("/api");

    Files.createDirectories(EXPORT.getParent());
    Files.writeString(EXPORT, response.getBody());
  }

  @Test
  void servesSwaggerUi() {
    ResponseEntity<String> response =
        rest.getForEntity("http://localhost:" + port + "/api/docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
