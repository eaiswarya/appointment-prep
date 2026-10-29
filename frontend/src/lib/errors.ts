// User-facing messages for the API error codes in TECHNICAL_DESIGN §9.3.
// Every error the UI shows goes through errorMessage().

export const ERROR_MESSAGES = {
  VALIDATION_ERROR: 'Some fields need attention. Check the highlighted fields and try again.',
  INVALID_DURATION: 'Durations must be between 5 and 480 minutes, in steps of 5.',
  RANGE_TOO_LARGE: 'That date range is too large. Choose a shorter range.',
  UNAUTHENTICATED: 'Please log in to continue.',
  TOKEN_EXPIRED: 'Your session has expired. Please log in again.',
  FORBIDDEN: "You don't have permission to do that.",
  NOT_FOUND: "We couldn't find what you were looking for.",
  SLOT_TAKEN: 'Sorry, that time was just booked by someone else. Please pick another time.',
  CUSTOMER_OVERLAP: 'You already have an appointment at that time.',
  INVALID_STATE: "This booking can't be changed in its current state.",
  CONCURRENT_MODIFICATION: 'This was changed by someone else. Refresh and try again.',
  EMAIL_TAKEN: 'An account with this email already exists.',
  OUTSIDE_AVAILABILITY: "That time is outside the provider's working hours.",
  PROVIDER_TIME_OFF: 'The provider is unavailable at that time.',
  TOO_SOON: "That time is too soon to book. Please choose a later time.",
  TOO_FAR: 'That time is too far ahead to book yet.',
  SERVICE_NOT_OFFERED: "This provider doesn't offer that service.",
  LIMIT_REACHED: "You've reached the limit of upcoming bookings with this provider.",
  CANCEL_CUTOFF_PASSED: "It's too late to cancel this booking online.",
  RATE_LIMITED: 'Too many attempts. Please wait a moment and try again.',
} as const satisfies Record<string, string>

export type ErrorCode = keyof typeof ERROR_MESSAGES

export const FALLBACK_MESSAGE = 'Something went wrong. Please try again.'

export function errorMessage(code: string | undefined): string {
  if (code !== undefined && Object.hasOwn(ERROR_MESSAGES, code)) {
    return ERROR_MESSAGES[code as ErrorCode]
  }
  return FALLBACK_MESSAGE
}
