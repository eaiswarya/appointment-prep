import { Link } from 'react-router'

/** The admin area's start page. Services, bookings and settings arrive in later milestones. */
export function AdminHomePage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-2xl font-semibold">Admin</h1>
      <ul className="mt-6 space-y-2">
        <li>
          <Link to="/admin/providers" className="font-medium text-blue-700 underline">
            Providers
          </Link>
          <span className="text-gray-600"> — invite providers and see who has joined</span>
        </li>
      </ul>
    </main>
  )
}
