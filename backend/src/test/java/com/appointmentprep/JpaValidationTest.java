package com.appointmentprep;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** Hibernate only validates the Flyway schema; it never creates or alters tables. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaValidationTest {

  @Autowired private Environment env;

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  void startsWithSchemaValidationAgainstFlyway() {
    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(env.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    assertThat(entityManagerFactory.isOpen()).isTrue();
  }
}
