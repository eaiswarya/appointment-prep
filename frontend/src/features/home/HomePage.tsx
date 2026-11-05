import { Link } from 'react-router'

// Placeholder until the provider directory (M2) takes over `/`.
export function HomePage() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-12">
      <h1 className="text-3xl font-semibold">Appointment Prep</h1>
      <p className="mt-2 text-gray-600">Book time with a provider.</p>
      <p className="mt-6">
        <Link to="/register" className="font-medium text-blue-700 underline">
          Create an account
        </Link>
      </p>
    </main>
  )
}
