package com.appointmentprep.user;

import com.appointmentprep.common.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository users;

  public UserService(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public UserDto get(UUID id) {
    return users
        .findById(id)
        .map(UserDto::from)
        .orElseThrow(() -> new NotFoundException("User not found."));
  }
}
