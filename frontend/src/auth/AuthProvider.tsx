import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import { ApiError } from '../lib/errors'
import { setAccessToken } from './tokenStore'
import { AuthContext, type AuthState, type User } from './useAuth'

/** Holds the signed-in user in React state and the access token in memory (§8.1, §10.1). */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)

  const login = useCallback(async (email: string, password: string) => {
    const { data, error, response } = await api.POST('/auth/login', { body: { email, password } })
    if (error !== undefined || !response.ok || !data) {
      throw new ApiError(response.status, error)
    }
    setAccessToken(data.accessToken)
    setUser(data.user)
    return data.user
  }, [])

  const value = useMemo<AuthState>(() => ({ user, login }), [user, login])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
