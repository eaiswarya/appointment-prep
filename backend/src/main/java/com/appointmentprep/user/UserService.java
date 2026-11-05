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

  @Transactional
  public UserDto updateProfile(UUID id, UpdateProfileRequest request) {
    User user = find(id);
    user.updateProfile(request.fullName(), request.timezone(), clock.instant());
    return UserDto.from(user);
  }

  private User find(UUID id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
  }
}
