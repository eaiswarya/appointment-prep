package com.appointmentprep.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

  @Test
  void rejectsASecretShorterThan256Bits() {
    assertThatThrownBy(() -> JwtConfig.secretKey("too-short-31-bytes-0123456789ab"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_JWT_SECRET");
  }

  @Test
  void rejectsAMissingSecret() {
    assertThatThrownBy(() -> JwtConfig.secretKey("  ")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void accepts32Bytes() {
    assertThatCode(() -> JwtConfig.secretKey("0123456789abcdef0123456789abcdef"))
        .doesNotThrowAnyException();
  }
}
