import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router'

// Placeholder: shows the post-registration notice. The login form arrives in #21.
export function LoginPage() {
  const state = useLocation().state as { registered?: boolean; email?: string } | null
  const heading = useRef<HTMLHeadingElement>(null)

  // Move focus after navigation so screen readers announce the page and its notice.
  useEffect(() => {
    heading.current?.focus()
  }, [])

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 ref={heading} tabIndex={-1} className="text-2xl font-semibold focus:outline-none">
        Log in
      </h1>
      {state?.registered && (
        <p role="status" className="mt-4 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900">
          Your account is ready. You can log in now{state.email ? ` as ${state.email}` : ''}.
        </p>
      )}
    </main>
  )
}
