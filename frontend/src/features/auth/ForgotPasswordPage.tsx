import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api } from '../../api/client'
import { FormField } from '../../components/FormField'
import { ApiError, errorMessage } from '../../lib/errors'

const schema = z.object({ email: z.email('Enter a valid email address') })

type FormValues = z.infer<typeof schema>

/** POST /api/auth/forgot-password (FR-3). The answer never reveals whether the account exists. */
export function ForgotPasswordPage() {
  const {
    register,
    handleSubmit,
    setError,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const send = useMutation({
    mutationFn: async (body: FormValues) => {
      const { error, response } = await api.POST('/auth/forgot-password', { body })
      if (error !== undefined || !response.ok) {
        throw new ApiError(response.status, error)
      }
    },
    onError: (error) => {
      const code = error instanceof ApiError ? error.problem.code : undefined
      setError('root', { message: errorMessage(code) })
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Reset your password</h1>
      <p className="mt-2 text-sm text-gray-600">
        Enter your account's email and we'll send you a link to choose a new password.
      </p>

      {/* Always in the DOM, so screen readers announce the confirmation when it appears. */}
      <p
        role="status"
        className={
          send.isSuccess ? 'mt-6 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'
        }
      >
        {send.isSuccess
          ? `If an account exists for ${getValues('email')}, we've sent a link to reset your password. It expires in 1 hour.`
          : ''}
      </p>

      <form noValidate className="mt-6 space-y-4" onSubmit={handleSubmit((values) => send.mutate(values))}>
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
        <button
          type="submit"
          disabled={send.isPending}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {send.isPending ? 'Sending…' : 'Send reset link'}
        </button>
      </form>

      <p className="mt-6 text-sm text-gray-600">
        Remembered it?{' '}
        <Link to="/login" className="font-medium text-blue-700 underline">
          Log in
        </Link>
      </p>
    </main>
  )
}
