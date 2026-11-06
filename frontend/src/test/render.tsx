import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { StrictMode } from 'react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { AuthProvider } from '../auth/AuthProvider'
import { routes } from '../router'

/**
 * Renders the real routes at `path` with fresh auth and query state (no retries, so errors show
 * at once). `state` becomes the location state, e.g. `{ from: '/profile' }`.
 */
export function renderRoute(path: string, state?: unknown) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  // Split `path` so a query such as ?token=… reaches useSearchParams.
  const { pathname, search, hash } = new URL(path, 'http://localhost')
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname, search, hash, state }] })
  // StrictMode as in main.tsx: effects run twice, which the session restore must survive
  // (one shared refresh, not two that would look like token reuse).
  render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </StrictMode>,
  )
  return { router }
}
