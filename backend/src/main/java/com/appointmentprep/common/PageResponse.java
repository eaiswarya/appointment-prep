package com.appointmentprep.common;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** A page of a list endpoint (TECHNICAL_DESIGN §9): {@code {items, page, size, total}}. */
public record PageResponse<T>(
    @Schema(requiredMode = REQUIRED) List<T> items,
    @Schema(requiredMode = REQUIRED) int page,
    @Schema(requiredMode = REQUIRED) int size,
    @Schema(requiredMode = REQUIRED) long total) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
