import { Link } from 'react-router'

/** The provider area's start page. Time off is added in M2. */
export function ProviderHomePage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-2xl font-semibold">Provider area</h1>
      <ul className="mt-6 space-y-2">
        <li>
          <Link to="/provider/profile" className="font-medium text-blue-700 underline">
            Profile
          </Link>
          <span className="text-gray-600"> — your display name, bio, time zone and buffer</span>
        </li>
        <li>
          <Link to="/provider/services" className="font-medium text-blue-700 underline">
            Services
          </Link>
          <span className="text-gray-600"> — the services customers can book you for</span>
        </li>
        <li>
          <Link to="/provider/availability" className="font-medium text-blue-700 underline">
            Weekly hours
          </Link>
          <span className="text-gray-600"> — when you work, day by day</span>
        </li>
      </ul>
    </main>
  )
}
