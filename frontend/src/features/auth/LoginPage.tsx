import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../../auth/useAuth'
import { ApiError, errorMessage } from '../../lib/errors'
import { FormField } from '../../components/FormField'

const schema = z.object({
  email: z.email('Enter your email address'),
  password: z.string().trim().min(1, 'Enter your password'),
})

type FormValues = z.infer<typeof schema>

type LocationState = {
  registered?: boolean
  passwordReset?: boolean
  email?: string
  from?: string
} | null

export function LoginPage() {
  const state = useLocation().state as LocationState
  const navigate = useNavigate()
  const { login } = useAuth()
  const heading = useRef<HTMLHeadingElement>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: state?.email ?? '', password: '' },
  })

  // Move focus after navigation so screen readers announce the page and any notice.
  useEffect(() => {
    heading.current?.focus()
  }, [])

  const signIn = useMutation({
    mutationFn: (values: FormValues) => login(values.email, values.password),
    onSuccess: () => navigate(state?.from ?? '/', { replace: true }),
    onError: (error) => {
      const code = error instanceof ApiError ? error.problem.code : undefined
      // Don't reveal whether the email exists: one message for every bad-credentials case.
      const message =
        code === 'UNAUTHENTICATED' ? 'The email or password is incorrect.' : errorMessage(code)
      setError('root', { message })
    },
  })

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
      {state?.passwordReset && (
        <p role="status" className="mt-4 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900">
          Your password has been changed. Log in with your new password.
        </p>
      )}

      <form noValidate className="mt-6 space-y-4" onSubmit={handleSubmit((values) => signIn.mutate(values))}>
        {errors.root && (
          <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errors.root.message}
          </p>
        )}
        <FormField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <button
          type="submit"
          disabled={signIn.isPending}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {signIn.isPending ? 'Logging in…' : 'Log in'}
        </button>
        <p className="text-sm">
          <Link to="/forgot-password" className="text-blue-700 underline">
            Forgot your password?
          </Link>
        </p>
      </form>

      <p className="mt-6 text-sm text-gray-600">
        New here?{' '}
        <Link to="/register" className="font-medium text-blue-700 underline">
          Create an account
        </Link>
      </p>
    </main>
  )
}
