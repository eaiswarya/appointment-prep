package com.appointmentprep.auth;

import com.appointmentprep.user.UserDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final RegistrationService registration;
  private final LoginService login;
  private final RefreshTokenService refreshTokens;
  private final AuthCookies cookies;
  private final PasswordResetService passwordReset;

  public AuthController(
      RegistrationService registration,
      LoginService login,
      RefreshTokenService refreshTokens,
      AuthCookies cookies,
      PasswordResetService passwordReset) {
    this.registration = registration;
    this.login = login;
    this.refreshTokens = refreshTokens;
    this.cookies = cookies;
    this.passwordReset = passwordReset;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserDto register(@Valid @RequestBody RegisterRequest request) {
    return registration.register(request);
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginService.LoginResult result = login.login(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.refresh(result.refreshToken()).toString())
        .body(new LoginResponse(result.accessToken(), result.user()));
  }

  /** Rotates the refresh cookie and returns a new access token (silent refresh). */
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RefreshResponse> refresh(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
    RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.refresh(rotation.refreshToken()).toString())
        .body(new RefreshResponse(rotation.accessToken()));
  }

  /** Always 204: revokes the token if there is one and tells the browser to drop the cookie. */
  @PostMapping("/logout")
  @ApiResponse(responseCode = "204", description = "Logged out; the refresh cookie is cleared.")
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false) String refreshToken) {
    refreshTokens.revoke(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
        .build();
  }

  /** Always 202 with no body: whether the email exists is never revealed. */
  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    passwordReset.requestReset(request.email());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    passwordReset.resetPassword(request.token(), request.newPassword());
  }
}
