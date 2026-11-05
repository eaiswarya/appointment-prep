import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth, type Role } from './useAuth'

/**
 * Route guard for UX only: the API enforces access (FR-4). Visitors who aren't logged in go to
 * /login and come back afterwards; users with another role go home. While the session is being
 * restored (page load), it renders nothing rather than redirecting too early.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user, status } = useAuth()
  const location = useLocation()

  if (status === 'restoring') {
    return null
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search + location.hash }} />
  }
  if (user.role !== role) {
    return <Navigate to="/" replace />
  }
  return children
}
