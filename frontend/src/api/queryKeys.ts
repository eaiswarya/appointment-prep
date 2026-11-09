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
  /** The public catalog, GET /services. */
  services: ['services'] as const,
}
