package com.appointmentprep.catalog;

import com.appointmentprep.common.DomainException;
import com.appointmentprep.common.ErrorCode;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.common.NotFoundException;
import com.appointmentprep.common.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The service catalog (A2): admins create, edit and deactivate; everyone reads active ones. */
@Service
public class CatalogService {

  // Mirrors the services.duration_minutes CHECK (TECHNICAL_DESIGN §5.2).
  static final int MIN_DURATION = 5;
  static final int MAX_DURATION = 480;
  static final int DURATION_STEP = 5;

  private final CatalogItemRepository services;
  private final IdGenerator ids;

  public CatalogService(CatalogItemRepository services, IdGenerator ids) {
    this.services = services;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public List<ServiceDto> listActive() {
    return services.findActive().stream().map(ServiceDto::of).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<ServiceDto> listAll(int page, int size) {
    return PageResponse.of(services.findAllByName(PageRequest.of(page, size)).map(ServiceDto::of));
  }

  @Transactional
  public ServiceDto create(CreateServiceRequest request) {
    checkDuration(request.durationMinutes());
    CatalogItem item =
        CatalogItem.create(
            ids.next(),
            request.name().strip(),
            normalize(request.description()),
            request.durationMinutes());
    return ServiceDto.of(services.save(item));
  }

  @Transactional
  public ServiceDto update(UUID id, UpdateServiceRequest request) {
    CatalogItem item =
        services.findById(id).orElseThrow(() -> new NotFoundException("No such service."));
    if (request.durationMinutes() != null) {
      checkDuration(request.durationMinutes());
      item.changeDuration(request.durationMinutes());
    }
    if (request.name() != null) {
      item.rename(request.name().strip());
    }
    if (request.description() != null) {
      item.describe(normalize(request.description()));
    }
    if (request.active() != null) {
      item.setActive(request.active());
    }
    return ServiceDto.of(item);
  }

  private static void checkDuration(int minutes) {
    if (minutes < MIN_DURATION || minutes > MAX_DURATION || minutes % DURATION_STEP != 0) {
      throw new DomainException(
          ErrorCode.INVALID_DURATION,
          "A duration must be between 5 and 480 minutes, in steps of 5.");
    }
  }

  /** Blank descriptions are stored as none. */
  private static String normalize(String description) {
    return description == null || description.isBlank() ? null : description.strip();
  }
}
