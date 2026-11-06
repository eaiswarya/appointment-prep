import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { api } from '../../api/client'
import { FormField } from '../../components/FormField'
import { ApiError, errorMessage } from '../../lib/errors'
import { useFragmentToken } from './useFragmentToken'

// Quick client checks; the server applies the full §8.1 rules (e.g. common passwords).
const schema = z
  .object({
    password: z.string().min(8, 'Use at least 8 characters'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match',
  })

type FormValues = z.infer<typeof schema>

/** /accept-invite#token=… from the invite email: an invited provider sets their first password (FR-1). */
export function AcceptInvitePage() {
  const token = useFragmentToken()
  const navigate = useNavigate()
  const [linkInvalid, setLinkInvalid] = useState(!token)
  const alertRef = useRef<HTMLDivElement>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  // When the link turns out to be invalid, move focus to the explanation (the form disappears).
  useEffect(() => {
    if (linkInvalid) {
      alertRef.current?.focus()
    }
  }, [linkInvalid])

  const accept = useMutation({
    mutationFn: async (password: string) => {
      const { error, response } = await api.POST('/auth/accept-invite', {
        body: { token: token ?? '', password },
      })
      if (error !== undefined || !response.ok) {
        throw new ApiError(response.status, error)
      }
    },
    onSuccess: () => navigate('/login', { state: { inviteAccepted: true } }),
    onError: (error) => {
      const problem = error instanceof ApiError ? error.problem : {}
      if (problem.code === 'INVALID_TOKEN') {
        setLinkInvalid(true)
        return
      }
      const passwordError = problem.errors?.find((e) => e.field === 'password')
      if (passwordError) {
        const message = passwordError.message
        setError('password', { message: message.charAt(0).toUpperCase() + message.slice(1) }, { shouldFocus: true })
        return
      }
      setError('root', { message: errorMessage(problem.code) })
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Set up your provider account</h1>

      {linkInvalid ? (
        <div
          ref={alertRef}
          tabIndex={-1}
          role="alert"
          className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800 focus:outline-none"
        >
          <p>{errorMessage('INVALID_TOKEN')}</p>
          {/* Only an admin can send a new invite; there's no self-service link here. */}
          <p className="mt-2">Ask your admin for a new invite.</p>
        </div>
      ) : (
        <>
          <p className="mt-2 text-sm text-gray-600">Choose a password to finish setting up your account.</p>
          <form
            noValidate
            className="mt-6 space-y-4"
            onSubmit={handleSubmit((values) => accept.mutate(values.password))}
          >
            {errors.root && (
              <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
                {errors.root.message}
              </p>
            )}
            <FormField
              id="password"
              label="Password"
              type="password"
              autoComplete="new-password"
              hint="At least 8 characters. Avoid common passwords."
              error={errors.password?.message}
              {...register('password')}
            />
            <FormField
              id="confirmPassword"
              label="Confirm password"
              type="password"
              autoComplete="new-password"
              error={errors.confirmPassword?.message}
              {...register('confirmPassword')}
            />
            <button
              type="submit"
              disabled={accept.isPending}
              className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
            >
              {accept.isPending ? 'Saving…' : 'Set password'}
            </button>
          </form>
        </>
      )}
    </main>
  )
}
