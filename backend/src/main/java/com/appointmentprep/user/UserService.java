package com.appointmentprep.user;

import com.appointmentprep.common.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;
  private final Clock clock;

  public UserService(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public UserDto get(UUID id) {
    return UserDto.from(find(id));
  }

  /**
   * Only active users: a deactivated user may still hold a valid access token for up to 15 minutes
   * (§8.1), but must not write to their account.
   */
  @Transactional
  public UserDto updateProfile(UUID id, UpdateProfileRequest request) {
    User user = users.findById(id).filter(User::isActive).orElseThrow(UserService::notFound);
    user.updateProfile(request.fullName(), request.timezone(), clock.instant());
    return UserDto.from(user);
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(UserService::notFound);
  }

  private static NotFoundException notFound() {
    return new NotFoundException("User not found.");
  }
}
