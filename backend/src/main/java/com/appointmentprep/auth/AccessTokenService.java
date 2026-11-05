package com.appointmentprep.auth;

import com.appointmentprep.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens: claims {@code sub}, {@code role}, {@code exp} (§8.1). */
@Service
public class AccessTokenService {

  static final Duration TTL = Duration.ofMinutes(15);

  private final JwtEncoder encoder;
  private final Clock clock;

  public AccessTokenService(JwtEncoder encoder, Clock clock) {
    this.encoder = encoder;
    this.clock = clock;
  }

  public String issue(User user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
