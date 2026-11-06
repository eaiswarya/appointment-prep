package com.appointmentprep.provider;

import com.appointmentprep.auth.InviteService;
import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.common.PageResponse;
import com.appointmentprep.user.User;
import com.appointmentprep.user.UserRepository;
import java.time.Clock;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Providers (A1). M1 covers inviting and listing; editing and deactivating arrive in M2. */
@Service
public class ProviderService {

  private static final String EMAIL_UNIQUE = "users_email_unique";

  private final UserRepository users;
  private final ProviderRepository providers;
  private final InviteService invites;
  private final IdGenerator ids;
  private final Clock clock;

  public ProviderService(
      UserRepository users,
      ProviderRepository providers,
      InviteService invites,
      IdGenerator ids,
      Clock clock) {
    this.users = users;
    this.providers = providers;
    this.invites = invites;
    this.ids = ids;
    this.clock = clock;
  }

  /**
   * Creates a PROVIDER user without a password, their provider profile, and an invite (token +
   * ACCOUNT_INVITE outbox row), all in one transaction.
   */
  @Transactional
  public AdminProviderDto invite(InviteProviderRequest request) {
    String email = request.email().trim();
    // Friendly pre-check; the case-insensitive unique constraint on users.email is the real guard.
    if (users.emailExists(email)) {
      throw emailTaken();
    }
    String fullName = request.fullName().strip();
    String displayName = request.displayName().strip();
    User user = User.newProvider(ids.next(), email, fullName, request.timezone(), clock.instant());
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      if (violates(e, EMAIL_UNIQUE)) {
        throw emailTaken();
      }
      throw e;
    }
    Provider provider = Provider.create(ids.next(), user.getId(), displayName, request.timezone());
    providers.save(provider);
    invites.sendInvite(user, provider.getTimezone());
    return new AdminProviderDto(
        provider.getId(),
        user.getEmail(),
        user.getFullName(),
        provider.getDisplayName(),
        provider.getTimezone(),
        provider.isActive(),
        true);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminProviderDto> list(int page, int size) {
    return PageResponse.of(providers.findAllForAdmin(PageRequest.of(page, size)));
  }

  private static DomainException emailTaken() {
    return new DomainException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists.");
  }

  private static boolean violates(Throwable error, String constraint) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation
          && constraint.equals(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }
}
