import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth, type Role } from './useAuth'

/**
 * Route guard for UX only: the API enforces access (FR-4). Visitors who aren't logged in go to
 * /login and come back afterwards; users with another role go home.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (user.role !== role) {
    return <Navigate to="/" replace />
  }
  return children
}
