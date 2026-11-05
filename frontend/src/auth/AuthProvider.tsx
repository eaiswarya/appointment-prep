import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshAccessToken } from '../api/client'
import { ApiError } from '../lib/errors'
import { onSessionExpired, setAccessToken } from './tokenStore'
import { AuthContext, type AuthState, type User } from './useAuth'

/**
 * Holds the signed-in user in React state and the access token in memory (§8.1, §10.1). On load
 * it restores the session from the refresh cookie, so a page reload keeps the user logged in.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthState['status']>('restoring')

  useEffect(() => {
    let cancelled = false
    async function restore() {
      const token = await refreshAccessToken()
      if (token) {
        const { data } = await api.GET('/me')
        if (!cancelled && data) {
          setUser(data)
        }
      }
      if (!cancelled) {
        setStatus('ready')
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => onSessionExpired(() => setUser(null)), [])

  const login = useCallback(async (email: string, password: string) => {
    const { data, error, response } = await api.POST('/auth/login', { body: { email, password } })
    if (error !== undefined || !response.ok || !data) {
      throw new ApiError(response.status, error)
    }
    setAccessToken(data.accessToken)
    setUser(data.user)
    return data.user
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.POST('/auth/logout')
    } finally {
      // Forget the session locally even if the server call failed.
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, status, login, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
