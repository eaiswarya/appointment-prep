package com.appointmentprep.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {}
