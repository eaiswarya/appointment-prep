import { createContext, useContext } from 'react'
import type { components } from '../api/schema'

export type User = components['schemas']['UserDto']
export type Role = User['role']

export interface AuthState {
  /** The signed-in user, or null. */
  user: User | null
  /** 'restoring' until the startup refresh has finished; guards wait for 'ready'. */
  status: 'restoring' | 'ready'
  /** Logs in; rejects with ApiError on failure. */
  login: (email: string, password: string) => Promise<User>
  /** Replaces the signed-in user, e.g. after the profile was saved. */
  updateUser: (user: User) => void
  /** Revokes the refresh token on the server, then forgets the user here. Rejects if it fails. */
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}
