package com.appointmentprep.config;

import com.appointmentprep.auth.JwtConfig;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.Problems;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Stateless JWT security (TECHNICAL_DESIGN §8.1, §8.2). Paths are relative to the /api context
 * path. Ownership checks live in the services; these are only the URL rules.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain api(
      HttpSecurity http, AuthenticationEntryPoint entryPoint, AccessDeniedHandler deniedHandler)
      throws Exception {
    http
        // Bearer tokens, not cookies, authenticate API calls; the refresh cookie is
        // SameSite=Strict.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/auth/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // API docs are only enabled in the dev profile.
                    .requestMatchers("/docs", "/docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/providers/**", "/services")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/provider/**")
                    .hasRole("PROVIDER")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .bearerTokenResolver(ignoringAuthPaths())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter()))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler));
    return http.build();
  }

  /**
   * Ignores any Bearer header on /auth/** (login, refresh, logout). The SPA may still hold an
   * expired access token; it must not turn those public calls into 401 TOKEN_EXPIRED.
   */
  private static BearerTokenResolver ignoringAuthPaths() {
    DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
    return request -> {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      return path.startsWith("/auth/") ? null : bearer.resolve(request);
    };
  }

  /** The {@code role} claim (e.g. CUSTOMER) becomes the authority ROLE_CUSTOMER. */
  private static JwtAuthenticationConverter roleClaimConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  /** 401 as problem+json: TOKEN_EXPIRED for an expired token, UNAUTHENTICATED otherwise. */
  @Bean
  AuthenticationEntryPoint problemEntryPoint(Problems problems) {
    return (request, response, ex) -> {
      // RFC 6750 §3: name the error when a token was sent but rejected.
      response.setHeader(
          "WWW-Authenticate",
          ex instanceof OAuth2AuthenticationException
              ? "Bearer error=\"invalid_token\""
              : "Bearer");
      if (isExpired(ex)) {
        problems.write(request, response, ErrorCode.TOKEN_EXPIRED, "The access token has expired.");
      } else {
        problems.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required.");
      }
    };
  }

  /** 403 as problem+json: authenticated, but the role isn't allowed here. */
  @Bean
  AccessDeniedHandler problemDeniedHandler(Problems problems) {
    return (request, response, ex) ->
        problems.write(request, response, ErrorCode.FORBIDDEN, "You don't have access to this.");
  }

  private static boolean isExpired(AuthenticationException ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      if (t instanceof JwtValidationException invalid
          && invalid.getErrors().stream()
              .anyMatch(e -> JwtConfig.TOKEN_EXPIRED_ERROR.equals(e.getErrorCode()))) {
        return true;
      }
    }
    return false;
  }
}
