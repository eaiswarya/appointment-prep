package com.appointmentprep.provider;

import com.appointmentprep.common.FieldValidationException;
import com.appointmentprep.common.GlobalExceptionHandler.FieldProblem;
import com.appointmentprep.common.IdGenerator;
import com.appointmentprep.common.NotFoundException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A provider's weekly working hours (P1; TECHNICAL_DESIGN §9.1). Rules are local times in the
 * provider's zone; this class never converts them. Overnight ranges aren't supported in v1: a range
 * ends on its own day, by 23:55 at the latest.
 */
@Service
public class AvailabilityService {

  private static final int GRID_MINUTES = 5;

  private final ProviderRepository providers;
  private final AvailabilityRuleRepository rules;
  private final IdGenerator ids;

  public AvailabilityService(
      ProviderRepository providers, AvailabilityRuleRepository rules, IdGenerator ids) {
    this.providers = providers;
    this.rules = rules;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public AvailabilityDto get(UUID userId) {
    return view(providers.findByUserId(userId).orElseThrow(AvailabilityService::notFound));
  }

  /**
   * Replaces all of the provider's rules. The whole set is checked first (grid, order, overlaps on
   * the same day); if anything is wrong, every problem is reported and nothing is saved. The
   * provider row is locked so concurrent saves run one after the other.
   */
  @Transactional
  public AvailabilityDto replace(UUID userId, UpdateAvailabilityRequest request) {
    Provider provider =
        providers
            .findByUserIdForUpdate(userId)
            .filter(Provider::isActive)
            .orElseThrow(AvailabilityService::notFound);
    List<AvailabilityRuleDto> requested = request.rules();
    check(requested);
    rules.deleteForProvider(provider.getId());
    for (AvailabilityRuleDto rule : requested) {
      rules.save(
          AvailabilityRule.create(
              ids.next(),
              provider.getId(),
              DayOfWeek.of(rule.dayOfWeek()),
              rule.startTime(),
              rule.endTime()));
    }
    rules.flush();
    return view(provider);
  }

  private static void check(List<AvailabilityRuleDto> requested) {
    List<FieldProblem> problems = new ArrayList<>();
    List<Integer> wellFormed = new ArrayList<>();
    for (int i = 0; i < requested.size(); i++) {
      AvailabilityRuleDto rule = requested.get(i);
      boolean ok = true;
      if (!onGrid(rule.startTime())) {
        problems.add(
            new FieldProblem(
                field(i, "startTime"), "must be on a 5-minute step, e.g. 09:00 or 09:05"));
        ok = false;
      }
      if (!onGrid(rule.endTime())) {
        problems.add(
            new FieldProblem(
                field(i, "endTime"), "must be on a 5-minute step, e.g. 17:00 or 17:05"));
        ok = false;
      } else if (!rule.startTime().isBefore(rule.endTime())) {
        problems.add(
            new FieldProblem(field(i, "endTime"), "must be after the start time, on the same day"));
        ok = false;
      }
      if (ok) {
        wellFormed.add(i);
      }
    }
    problems.addAll(overlaps(requested, wellFormed));
    if (!problems.isEmpty()) {
      throw new FieldValidationException(problems);
    }
  }

  /**
   * Same-day ranges must not overlap; touching is fine because the end is exclusive. The later
   * range in the request is the one reported.
   */
  private static List<FieldProblem> overlaps(
      List<AvailabilityRuleDto> requested, List<Integer> indexes) {
    List<FieldProblem> problems = new ArrayList<>();
    List<Integer> sorted = new ArrayList<>(indexes);
    sorted.sort(
        Comparator.<Integer>comparingInt(i -> requested.get(i).dayOfWeek())
            .thenComparing(i -> requested.get(i).startTime()));
    Integer reach = null; // the range on the current day that ends latest so far
    for (int i : sorted) {
      AvailabilityRuleDto rule = requested.get(i);
      if (reach != null && requested.get(reach).dayOfWeek().equals(rule.dayOfWeek())) {
        AvailabilityRuleDto earlier = requested.get(reach);
        if (rule.startTime().isBefore(earlier.endTime())) {
          AvailabilityRuleDto other = i > reach ? earlier : rule;
          problems.add(
              new FieldProblem(
                  "rules[" + Math.max(i, reach) + "]",
                  "overlaps " + other.startTime() + "–" + other.endTime() + " on the same day"));
          if (!rule.endTime().isAfter(earlier.endTime())) {
            continue; // the earlier range still reaches further
          }
        }
      }
      reach = i;
    }
    return problems;
  }

  private static boolean onGrid(LocalTime time) {
    return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % GRID_MINUTES == 0;
  }

  private static String field(int index, String name) {
    return "rules[" + index + "]." + name;
  }

  private AvailabilityDto view(Provider provider) {
    return new AvailabilityDto(
        provider.getTimezone(),
        rules.findForProvider(provider.getId()).stream().map(AvailabilityRuleDto::of).toList());
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Provider profile not found.");
  }
}
