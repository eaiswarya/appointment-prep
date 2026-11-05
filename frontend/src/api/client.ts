import createClient from 'openapi-fetch'
import { expireSession, getAccessToken, setAccessToken } from '../auth/tokenStore'
import type { paths } from './schema'

// Typed by the generated schema (`npm run generate:api`). Same origin: Vite in dev and
// Nginx in production proxy /api to the backend. Absolute, so it also works under Node in tests.
const options = {
  baseUrl: `${globalThis.location?.origin ?? ''}/api`,
  // Look fetch up per call instead of capturing it at import, so test mocks (MSW) can patch it.
  fetch: (request: Request) => globalThis.fetch(request),
}

export const api = createClient<paths>(options)

// No middleware, so refreshing can never recurse into the 401 handling below.
const authApi = createClient<paths>(options)

let refreshing: Promise<string | null> | null = null

/**
 * Swaps the refresh cookie for a new access token (TECHNICAL_DESIGN §8.1). Concurrent callers
 * share one in-flight request, so a burst of 401s triggers a single refresh.
 */
export function refreshAccessToken(): Promise<string | null> {
  refreshing ??= authApi
    .POST('/auth/refresh')
    .then(({ data, response }) => {
      const token = response.ok && data ? data.accessToken : null
      setAccessToken(token)
      return token
    })
    .catch(() => {
      setAccessToken(null)
      return null
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

// Copies of outgoing requests, so a 401 can be retried once with a fresh token.
const retryable = new Map<string, Request>()

api.use({
  // Send the in-memory access token, except to /auth/*: those calls are public, and a stale
  // token must not get in the way of logging in or refreshing.
  onRequest({ request, schemaPath, id }) {
    if (schemaPath.startsWith('/auth/')) {
      return request
    }
    const token = getAccessToken()
    if (token) {
      request.headers.set('Authorization', `Bearer ${token}`)
    }
    retryable.set(id, request.clone())
    return request
  },

  // On a 401, refresh once and retry. If the refresh fails too, the session is over.
  async onResponse({ response, id }) {
    const original = retryable.get(id)
    retryable.delete(id)
    if (response.status !== 401 || !original) {
      return response
    }
    const token = await refreshAccessToken()
    if (!token) {
      expireSession()
      return response
    }
    original.headers.set('Authorization', `Bearer ${token}`)
    return globalThis.fetch(original)
  },
})
