package com.appointmentprep.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.appointmentprep.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/** The API docs are dev-only (TECHNICAL_DESIGN §9). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiDisabledByDefaultTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate rest;

  @Test
  void specIsNotServedOutsideDev() {
    assertThat(
            rest.getForEntity("http://localhost:" + port + "/api/docs/openapi.json", String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
