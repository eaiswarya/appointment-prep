// TanStack Query keys in one place, so mutations can invalidate exactly what they change.
export const queryKeys = {
  adminProviders: {
    all: ['admin', 'providers'] as const,
    page: (page: number) => ['admin', 'providers', page] as const,
  },
  adminServices: {
    all: ['admin', 'services'] as const,
    page: (page: number) => ['admin', 'services', page] as const,
  },
  /** The calling provider's profile, GET /provider/me. */
  providerProfile: ['provider', 'me'] as const,
  /** The calling provider's weekly hours, GET /provider/availability. */
  providerAvailability: ['provider', 'availability'] as const,
  /** The services the calling provider offers, GET /provider/services. */
  providerServices: ['provider', 'services'] as const,
  /** The public catalog, GET /services. */
  services: ['services'] as const,
}
