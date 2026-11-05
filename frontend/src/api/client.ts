import createClient from 'openapi-fetch'
import { getAccessToken } from '../auth/tokenStore'
import type { paths } from './schema'

// Typed by the generated schema (`npm run generate:api`). Same origin: Vite in dev and
// Nginx in production proxy /api to the backend. Absolute, so it also works under Node in tests.
export const api = createClient<paths>({
  baseUrl: `${globalThis.location?.origin ?? ''}/api`,
  // Look fetch up per call instead of capturing it at import, so test mocks (MSW) can patch it.
  fetch: (request) => globalThis.fetch(request),
})

// Send the in-memory access token (TECHNICAL_DESIGN §8.1), except to /auth/*: those calls are
// public, and a stale token must not get in the way of logging in or refreshing.
api.use({
  onRequest({ request, schemaPath }) {
    const token = getAccessToken()
    if (token && !schemaPath.startsWith('/auth/')) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    return request
  },
})
