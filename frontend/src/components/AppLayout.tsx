import { useEffect, useState } from 'react'
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
        navigate('/login', {
          replace: true,
          state: { from: location.pathname + location.search + location.hash },
        }),
      ),
    [navigate, location],
  )

  const [logoutFailed, setLogoutFailed] = useState(false)

  async function handleLogout() {
    setLogoutFailed(false)
    try {
      await logout()
      navigate('/')
    } catch {
      setLogoutFailed(true)
    }
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
                {user.role === 'PROVIDER' && (
                  <Link to="/provider" className="whitespace-nowrap text-blue-700 underline">
                    Provider area
                  </Link>
                )}
                <p className="min-w-0">
                  <span className="sr-only">Signed in as </span>
                  <Link to="/profile" title="Your profile" className="inline-block max-w-[12rem] truncate align-bottom font-medium text-blue-700 underline">
                    {user.fullName}
                  </Link>
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
      {logoutFailed && (
        <p role="alert" className="mx-auto mt-4 max-w-3xl rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          We couldn't log you out. Check your connection and try again.
        </p>
      )}
      <Outlet />
    </>
  )
}
