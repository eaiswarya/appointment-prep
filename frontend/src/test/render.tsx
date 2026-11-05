import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
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
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: path, state }] })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return { router }
}
