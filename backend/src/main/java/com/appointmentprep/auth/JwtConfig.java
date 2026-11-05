package com.appointmentprep.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HS256 access tokens signed with {@code APP_JWT_SECRET} (TECHNICAL_DESIGN §8.1, §12.3). */
@Configuration
public class JwtConfig {

  /** OAuth2 error code our validator uses, so the entry point can answer TOKEN_EXPIRED. */
  public static final String TOKEN_EXPIRED_ERROR = "token_expired";

  private static final int MIN_SECRET_BYTES = 32;

  /** Fails fast on a missing or short secret: HS256 needs at least 256 bits of key. */
  static SecretKey secretKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("APP_JWT_SECRET is not set.");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "APP_JWT_SECRET must be at least 256 bits (32 bytes); it is " + bytes.length + ".");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String secret) {
    return secretKey(secret);
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(expiryValidator(clock));
    return decoder;
  }

  /** Checks {@code exp} against the injected clock (never the system clock). */
  static OAuth2TokenValidator<Jwt> expiryValidator(Clock clock) {
    return jwt -> {
      if (jwt.getExpiresAt() == null) {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "The token has no expiry.", null));
      }
      if (!clock.instant().isBefore(jwt.getExpiresAt())) {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(TOKEN_EXPIRED_ERROR, "The access token has expired.", null));
      }
      return OAuth2TokenValidatorResult.success();
    };
  }
}
