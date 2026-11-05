package com.appointmentprep.auth;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserDto;
import com.appointmentprep.user.UserRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email + password login (FR-2). Every failure looks the same to the caller. */
@Service
public class LoginService {

  /** Result of a successful login: the access token, the user, and the raw refresh token. */
  public record LoginResult(String accessToken, UserDto user, String refreshToken) {

    /** Never print the tokens, e.g. if a result ends up in a log line. */
    @Override
    public String toString() {
      return "LoginResult[user=" + user.id() + ", accessToken=***, refreshToken=***]";
    }
  }

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenService accessTokens;
  private final RefreshTokenService refreshTokens;
  // Compared against when there is no user, so unknown emails take as long as wrong passwords.
  private final String dummyHash;

  public LoginService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AccessTokenService accessTokens,
      RefreshTokenService refreshTokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
    this.dummyHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
  }

  @Transactional
  public LoginResult login(LoginRequest request) {
    Optional<User> found = users.findByEmail(request.email().trim());
    String hash = found.map(User::getPasswordHash).orElse(null);
    boolean passwordMatches =
        passwordEncoder.matches(request.password(), hash != null ? hash : dummyHash);
    // Unknown email, wrong password, inactive user, or invite not yet accepted: same answer.
    if (found.isEmpty() || hash == null || !passwordMatches || !found.get().isActive()) {
      throw new DomainException(ErrorCode.UNAUTHENTICATED, "The email or password is incorrect.");
    }
    User user = found.get();
    return new LoginResult(
        accessTokens.issue(user), UserDto.from(user), refreshTokens.issue(user.getId()));
  }
}
