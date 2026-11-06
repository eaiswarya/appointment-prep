import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { api } from '../../api/client'
import { FormField } from '../../components/FormField'
import { ApiError, errorMessage } from '../../lib/errors'

// Quick client checks; the server applies the full §8.1 rules (e.g. common passwords).
const schema = z
  .object({
    newPassword: z.string().min(8, 'Use at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match',
  })

type FormValues = z.infer<typeof schema>

/** /reset-password?token=… from the emailed link (FR-3). */
export function ResetPasswordPage() {
  const token = useSearchParams()[0].get('token')
  const navigate = useNavigate()
  const [linkInvalid, setLinkInvalid] = useState(!token)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const reset = useMutation({
    mutationFn: async (newPassword: string) => {
      const { error, response } = await api.POST('/auth/reset-password', {
        body: { token: token ?? '', newPassword },
      })
      if (error !== undefined || !response.ok) {
        throw new ApiError(response.status, error)
      }
    },
    onSuccess: () => navigate('/login', { state: { passwordReset: true } }),
    onError: (error) => {
      const problem = error instanceof ApiError ? error.problem : {}
      if (problem.code === 'INVALID_TOKEN') {
        setLinkInvalid(true)
        return
      }
      const passwordError = problem.errors?.find((e) => e.field === 'newPassword')
      if (passwordError) {
        const message = passwordError.message
        setError('newPassword', { message: message.charAt(0).toUpperCase() + message.slice(1) }, { shouldFocus: true })
        return
      }
      setError('root', { message: errorMessage(problem.code) })
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Choose a new password</h1>

      {linkInvalid ? (
        <div role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          <p>{errorMessage('INVALID_TOKEN')}</p>
          <p className="mt-2">
            <Link to="/forgot-password" className="font-medium text-blue-700 underline">
              Request a new link
            </Link>
          </p>
        </div>
      ) : (
        <form
          noValidate
          className="mt-6 space-y-4"
          onSubmit={handleSubmit((values) => reset.mutate(values.newPassword))}
        >
          {errors.root && (
            <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
              {errors.root.message}
            </p>
          )}
          <FormField
            id="newPassword"
            label="New password"
            type="password"
            autoComplete="new-password"
            hint="At least 8 characters. Avoid common passwords."
            error={errors.newPassword?.message}
            {...register('newPassword')}
          />
          <FormField
            id="confirmPassword"
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />
          <button
            type="submit"
            disabled={reset.isPending}
            className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
          >
            {reset.isPending ? 'Saving…' : 'Set new password'}
          </button>
        </form>
      )}
    </main>
  )
}
