import { Link } from 'react-router'

/** The provider area's start page. Services, weekly hours and time off are added in M2. */
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
      </ul>
    </main>
  )
}
