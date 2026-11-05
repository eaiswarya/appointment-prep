import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

/** Shared page frame: a header with the signed-in user's name, then the route's page. */
export function AppLayout() {
  const { user } = useAuth()

  return (
    <>
      <header className="border-b border-gray-200">
        <nav aria-label="Main" className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="font-semibold">
            Appointment Prep
          </Link>
          {user ? (
            <p className="text-sm">
              <span className="sr-only">Signed in as </span>
              <span className="font-medium">{user.fullName}</span>
            </p>
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
          )}
        </nav>
      </header>
      <Outlet />
    </>
  )
}
