import { http } from 'msw'
import { setupServer } from 'msw/node'
import { problem } from './fixtures'

// Every app load tries to restore the session; by default there is none (logged out).
// Tests add handlers with server.use(...). Any other request without a handler fails the test.
export const server = setupServer(
  http.post('*/api/auth/refresh', () => problem(401, { code: 'UNAUTHENTICATED' })),
)
