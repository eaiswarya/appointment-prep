package com.appointmentprep.auth;

import com.appointmentprep.user.UserDto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  static final String REFRESH_COOKIE = "refresh_token";

  private final RegistrationService registration;
  private final LoginService login;
  private final String cookiePath;

  public AuthController(
      RegistrationService registration,
      LoginService login,
      @Value("${server.servlet.context-path:}") String contextPath) {
    this.registration = registration;
    this.login = login;
    // Sent only to /api/auth/* (refresh, logout), never to other API calls.
    this.cookiePath = contextPath + "/auth";
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserDto register(@Valid @RequestBody RegisterRequest request) {
    return registration.register(request);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginService.LoginResult result = login.login(request);
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path(cookiePath)
            .maxAge(RefreshTokenService.TTL)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new LoginResponse(result.accessToken(), result.user()));
  }
}
