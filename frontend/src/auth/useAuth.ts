import { createContext, useContext } from 'react'
import type { components } from '../api/schema'

export type User = components['schemas']['UserDto']
export type Role = User['role']

export interface AuthState {
  /** The signed-in user, or null. */
  user: User | null
  /** Logs in; rejects with ApiError on failure. */
  login: (email: string, password: string) => Promise<User>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}
