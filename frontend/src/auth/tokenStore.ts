// The access token lives in memory only, never in localStorage/sessionStorage (TECHNICAL_DESIGN
// §8.1). A reload loses it; AuthProvider restores the session from the refresh cookie.
let accessToken: string | null = null

const sessionExpiredListeners = new Set<() => void>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** Called when the session can't be renewed (refresh failed). Returns an unsubscribe function. */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener)
  return () => {
    sessionExpiredListeners.delete(listener)
  }
}

/** Drops the token and tells listeners (AuthProvider clears the user, AppLayout goes to /login). */
export function expireSession(): void {
  accessToken = null
  sessionExpiredListeners.forEach((listener) => listener())
}
