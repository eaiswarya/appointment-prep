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

/** ok: new token. rejected: the server said no (401), the session is over. failed: try later. */
type RefreshResult = { kind: 'ok'; token: string } | { kind: 'rejected' } | { kind: 'failed' }

let refreshing: Promise<RefreshResult> | null = null

/**
 * Swaps the refresh cookie for a new access token (TECHNICAL_DESIGN §8.1). Concurrent callers in
 * this tab share one request; navigator.locks makes other tabs wait, so they send the new cookie.
 */
function refreshSession(): Promise<RefreshResult> {
  refreshing ??= withRefreshLock(requestRefresh).finally(() => {
    refreshing = null
  })
  return refreshing
}

/** The new access token, or null if the session couldn't be renewed. */
export async function refreshAccessToken(): Promise<string | null> {
  const result = await refreshSession()
  return result.kind === 'ok' ? result.token : null
}

async function requestRefresh(): Promise<RefreshResult> {
  const before = getAccessToken()
  try {
    const { data, response } = await authApi.POST('/auth/refresh')
    if (response.ok && data) {
      setAccessToken(data.accessToken)
      return { kind: 'ok', token: data.accessToken }
    }
    if (response.status === 401) {
      // Don't wipe a token that a login set while this request was in flight.
      if (getAccessToken() === before) {
        setAccessToken(null)
      }
      return { kind: 'rejected' }
    }
    return { kind: 'failed' }
  } catch {
    return { kind: 'failed' }
  }
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = globalThis.navigator?.locks
  return locks ? (locks.request('auth-refresh', task) as Promise<T>) : task()
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

  // On a 401, refresh once and retry. Only a rejected refresh ends the session; a failed one
  // (5xx, offline) returns the 401 and leaves the session for the next attempt.
  async onResponse({ response, id }) {
    const original = retryable.get(id)
    retryable.delete(id)
    if (response.status !== 401 || !original) {
      return response
    }
    const result = await refreshSession()
    if (result.kind === 'rejected') {
      expireSession()
    }
    if (result.kind !== 'ok') {
      return response
    }
    original.headers.set('Authorization', `Bearer ${result.token}`)
    return globalThis.fetch(original)
  },

  // Network errors skip onResponse; drop the copy so the map doesn't grow.
  onError({ id }) {
    retryable.delete(id)
  },
})
