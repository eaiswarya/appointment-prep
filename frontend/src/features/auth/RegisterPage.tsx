import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { api } from '../../api/client'
import type { components } from '../../api/schema'
import { ApiError, errorMessage } from '../../lib/errors'
import { FormField } from './FormField'

type RegisterRequest = components['schemas']['RegisterRequest']

// Quick client-side checks; the server applies the full rules (e.g. common passwords).
const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(120),
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'Use at least 8 characters'),
})

type FormValues = z.infer<typeof schema>

const FIELDS = ['fullName', 'email', 'password'] as const

export function RegisterPage() {
  const navigate = useNavigate()
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const signUp = useMutation({
    mutationFn: async (body: RegisterRequest) => {
      const { data, error, response } = await api.POST('/auth/register', { body })
      if (error !== undefined || !response.ok) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (user) => {
      navigate('/login', { state: { registered: true, email: user.email } })
    },
    onError: (error) => {
      const problem = error instanceof ApiError ? error.problem : {}
      if (problem.code === 'EMAIL_TAKEN') {
        setError('email', { message: errorMessage('EMAIL_TAKEN') }, { shouldFocus: true })
        return
      }
      const fieldErrors = (problem.errors ?? []).filter((e) =>
        (FIELDS as readonly string[]).includes(e.field),
      )
      if (fieldErrors.length > 0) {
        fieldErrors.forEach((e, i) =>
          setError(
            e.field as (typeof FIELDS)[number],
            { message: `${e.message.charAt(0).toUpperCase()}${e.message.slice(1)}` },
            { shouldFocus: i === 0 },
          ),
        )
        return
      }
      setError('root', { message: errorMessage(problem.code) })
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Create your account</h1>

      <form
        noValidate
        className="mt-6 space-y-4"
        onSubmit={handleSubmit((values) => signUp.mutate({ ...values, timezone }))}
      >
        {errors.root && (
          <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errors.root.message}
          </p>
        )}

        <FormField
          id="fullName"
          label="Full name"
          autoComplete="name"
          error={errors.fullName?.message}
          {...register('fullName')}
        />
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
          autoComplete="new-password"
          hint="At least 8 characters. Avoid common passwords."
          error={errors.password?.message}
          {...register('password')}
        />

        <p className="text-sm text-gray-600">
          Your time zone: <span className="font-medium">{timezone}</span>. You can change it later in
          your profile.
        </p>

        <button
          type="submit"
          disabled={signUp.isPending}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {signUp.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-6 text-sm text-gray-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-blue-700 underline">
          Log in
        </Link>
      </p>
    </main>
  )
}
