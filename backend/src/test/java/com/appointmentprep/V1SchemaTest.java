package com.appointmentprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V1 schema, and the exclusion constraints that are the source of truth for double bookings. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class V1SchemaTest {

  private static final Instant T10_00 = Instant.parse("2026-11-03T10:00:00Z");
  private static final Instant T10_30 = Instant.parse("2026-11-03T10:30:00Z");
  private static final Instant T10_40 = Instant.parse("2026-11-03T10:40:00Z");
  private static final Instant T09_30 = Instant.parse("2026-11-03T09:30:00Z");
  private static final Instant T10_15 = Instant.parse("2026-11-03T10:15:00Z");
  private static final Instant T11_00 = Instant.parse("2026-11-03T11:00:00Z");

  @Autowired private JdbcTemplate jdbc;

  private UUID customerA;
  private UUID customerB;
  private UUID providerP;
  private UUID providerQ;
  private UUID service;

  @BeforeEach
  void seed() {
    customerA = user("a@example.com", "CUSTOMER");
    customerB = user("b@example.com", "CUSTOMER");
    providerP = provider(user("p@example.com", "PROVIDER"));
    providerQ = provider(user("q@example.com", "PROVIDER"));
    service = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO services (id, name, duration_minutes) VALUES (?, 'Consultation', 30)",
        service);
  }

  @Test
  void createsEveryTable() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);

    assertThat(tables)
        .contains(
            "users",
            "providers",
            "services",
            "provider_services",
            "availability_rules",
            "time_off",
            "bookings",
            "booking_events",
            "email_outbox",
            "refresh_tokens",
            "user_tokens",
            "settings");
  }

  @Test
  void rejectsOverlappingProviderBooking() {
    booking(customerA, providerP, T10_00, T10_30, T10_30, "CONFIRMED");

    PSQLException error =
        violation(() -> booking(customerB, providerP, T10_15, T10_40, T10_40, "CONFIRMED"));

    assertThat(error.getSQLState()).isEqualTo("23P01");
    assertThat(error.getServerErrorMessage().getConstraint())
        .isEqualTo("bookings_no_provider_overlap");
  }

  @Test
  void allowsBackToBackBookings() {
    booking(customerA, providerP, T09_30, T10_00, T10_00, "CONFIRMED");

    booking(customerB, providerP, T10_00, T10_30, T10_30, "CONFIRMED");

    assertThat(confirmedBookingsFor(providerP)).isEqualTo(2);
  }

  @Test
  void rejectsBookingInsidePreviousBuffer() {
    // 09:30–10:00 plus a 15-minute buffer blocks the provider until 10:15.
    booking(customerA, providerP, T09_30, T10_00, T10_15, "CONFIRMED");

    PSQLException error =
        violation(() -> booking(customerB, providerP, T10_00, T10_30, T10_30, "CONFIRMED"));

    assertThat(error.getSQLState()).isEqualTo("23P01");
    assertThat(error.getServerErrorMessage().getConstraint())
        .isEqualTo("bookings_no_provider_overlap");
  }

  @Test
  void cancelledBookingDoesNotBlock() {
    booking(customerA, providerP, T10_00, T10_30, T10_30, "CANCELLED");

    booking(customerB, providerP, T10_00, T10_30, T10_30, "CONFIRMED");

    assertThat(confirmedBookingsFor(providerP)).isEqualTo(1);
  }

  @Test
  void rejectsCustomerDoubleBooking() {
    booking(customerA, providerP, T10_00, T11_00, T11_00, "CONFIRMED");

    PSQLException error =
        violation(() -> booking(customerA, providerQ, T10_30, T11_00, T11_00, "CONFIRMED"));

    assertThat(error.getSQLState()).isEqualTo("23P01");
    assertThat(error.getServerErrorMessage().getConstraint())
        .isEqualTo("bookings_no_customer_overlap");
  }

  @Test
  void seedsDefaultSettings() {
    Map<String, String> settings = new HashMap<>();
    jdbc.query(
        "SELECT key, value FROM settings",
        rs -> {
          settings.put(rs.getString("key"), rs.getString("value"));
        });

    assertThat(settings)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "booking.minNoticeMinutes", "120",
                "booking.horizonDays", "60",
                "booking.cancelCutoffMinutes", "1440",
                "booking.maxFuturePerProvider", "3",
                "notification.reminderLeadMinutes", "1440"));
  }

  private UUID user(String email, String role) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, full_name, role) VALUES (?, ?, ?, ?)",
        id,
        email,
        email,
        role);
    return id;
  }

  private UUID provider(UUID userId) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO providers (id, user_id, display_name, timezone) VALUES (?, ?, 'Dr P', 'Asia/Kolkata')",
        id,
        userId);
    return id;
  }

  private void booking(
      UUID customer,
      UUID provider,
      Instant start,
      Instant end,
      Instant blockedUntil,
      String status) {
    jdbc.update(
        """
        INSERT INTO bookings (id, customer_id, provider_id, service_id,
                              start_at, end_at, blocked_until, status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        customer,
        provider,
        service,
        utc(start),
        utc(end),
        utc(blockedUntil),
        status);
  }

  private int confirmedBookingsFor(UUID provider) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM bookings WHERE provider_id = ? AND status = 'CONFIRMED'",
        Integer.class,
        provider);
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static PSQLException violation(Runnable insert) {
    DataAccessException error = catchThrowableOfType(DataAccessException.class, insert::run);
    assertThat(error).as("expected the insert to be rejected").isNotNull();
    return (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
  }
}
