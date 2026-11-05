// The access token lives in memory only, never in localStorage/sessionStorage (TECHNICAL_DESIGN
// §8.1). A reload loses it; the refresh cookie restores the session (#22).
let accessToken: string | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}
