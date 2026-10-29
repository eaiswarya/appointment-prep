import { describe, expect, it } from 'vitest'
import { ERROR_MESSAGES, FALLBACK_MESSAGE, errorMessage } from './errors'

describe('errorMessage', () => {
  it('returns the specific message for a known code', () => {
    expect(errorMessage('SLOT_TAKEN')).toBe(ERROR_MESSAGES.SLOT_TAKEN)
    expect(ERROR_MESSAGES.SLOT_TAKEN).not.toBe(FALLBACK_MESSAGE)
  })

  it('returns the fallback for an unknown code', () => {
    expect(errorMessage('SOMETHING_NEW')).toBe(FALLBACK_MESSAGE)
  })

  it('returns the fallback when there is no code', () => {
    expect(errorMessage(undefined)).toBe(FALLBACK_MESSAGE)
  })

  it('covers every error code in TECHNICAL_DESIGN §9.3', () => {
    const designCodes = [
      'VALIDATION_ERROR', 'INVALID_DURATION', 'RANGE_TOO_LARGE',
      'UNAUTHENTICATED', 'TOKEN_EXPIRED',
      'FORBIDDEN',
      'NOT_FOUND',
      'SLOT_TAKEN', 'CUSTOMER_OVERLAP', 'INVALID_STATE', 'CONCURRENT_MODIFICATION', 'EMAIL_TAKEN',
      'OUTSIDE_AVAILABILITY', 'PROVIDER_TIME_OFF', 'TOO_SOON', 'TOO_FAR', 'SERVICE_NOT_OFFERED',
      'LIMIT_REACHED', 'CANCEL_CUTOFF_PASSED',
      'RATE_LIMITED',
    ]
    expect(Object.keys(ERROR_MESSAGES).sort()).toEqual([...designCodes].sort())
  })
})
