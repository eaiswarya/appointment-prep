import { useQueryClient } from '@tanstack/react-query'
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
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthState['status']>('restoring')

  useEffect(() => {
    let cancelled = false
    async function restore() {
      try {
        const token = await refreshAccessToken()
        if (token) {
          const { data } = await api.GET('/me')
          if (data) {
            if (!cancelled) setUser(data)
          } else {
            setAccessToken(null)
          }
        }
      } catch {
        // E.g. the network dropped between refresh and /me: start logged out, don't hang.
        setAccessToken(null)
      } finally {
        if (!cancelled) setStatus('ready')
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  // Forget the user and any cached data of theirs when the session can't be renewed.
  useEffect(
    () =>
      onSessionExpired(() => {
        setUser(null)
        queryClient.clear()
      }),
    [queryClient],
  )

  const login = useCallback(async (email: string, password: string) => {
    const { data, error, response } = await api.POST('/auth/login', { body: { email, password } })
    if (error !== undefined || !response.ok || !data) {
      throw new ApiError(response.status, error)
    }
    setAccessToken(data.accessToken)
    setUser(data.user)
    return data.user
  }, [])

  // Only forget the session once the server has revoked it; otherwise the cookie would still
  // restore it on the next load and the user would think they had logged out.
  const logout = useCallback(async () => {
    const { response } = await api.POST('/auth/logout')
    if (!response.ok) {
      throw new ApiError(response.status, undefined)
    }
    setAccessToken(null)
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({ user, status, login, logout }),
    [user, status, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
