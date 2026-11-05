import { useEffect } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router'
import { onSessionExpired } from '../auth/tokenStore'
import { useAuth } from '../auth/useAuth'

/** Shared page frame: a header with the signed-in user's name and log out, then the page. */
export function AppLayout() {
  const { user, status, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  // When the session can't be renewed, send the user to log in and bring them back afterwards.
  useEffect(
    () =>
      onSessionExpired(() =>
        navigate('/login', { state: { from: location.pathname + location.search + location.hash } }),
      ),
    [navigate, location],
  )

  async function handleLogout() {
    await logout()
    navigate('/')
  }

  return (
    <>
      <header className="border-b border-gray-200">
        <nav aria-label="Main" className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="font-semibold">
            Appointment Prep
          </Link>
          {status === 'ready' &&
            (user ? (
              <div className="flex items-center gap-4 text-sm">
                <p>
                  <span className="sr-only">Signed in as </span>
                  <span className="font-medium">{user.fullName}</span>
                </p>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="rounded px-2 py-1 text-blue-700 underline focus-visible:outline-2 focus-visible:outline-blue-700"
                >
                  Log out
                </button>
              </div>
            ) : (
              <ul className="flex gap-4 text-sm">
                <li>
                  <Link to="/login" className="text-blue-700 underline">
                    Log in
                  </Link>
                </li>
                <li>
                  <Link to="/register" className="text-blue-700 underline">
                    Create account
                  </Link>
                </li>
              </ul>
            ))}
        </nav>
      </header>
      <Outlet />
    </>
  )
}
