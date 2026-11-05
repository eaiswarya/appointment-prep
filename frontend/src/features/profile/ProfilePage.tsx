import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../../api/client'
import type { components } from '../../api/schema'
import { useAuth, type User } from '../../auth/useAuth'
import { FormField } from '../../components/FormField'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

type UpdateProfileRequest = components['schemas']['UpdateProfileRequest']

// Quick client checks only; the server validates the zone (browsers' lists differ, e.g. they may
// know only Asia/Calcutta while Asia/Kolkata is equally valid).
const schema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name').max(120),
  timezone: z.string().trim().min(1, 'Choose a time zone'),
})

type FormValues = z.infer<typeof schema>
type FieldName = keyof FormValues

const FIELDS: readonly string[] = ['fullName', 'timezone'] satisfies FieldName[]

/** Suggestions for the time zone field: the browser's IANA zones plus the user's current one. */
function timeZoneSuggestions(current: string): string[] {
  const zones = new Set(Intl.supportedValuesOf('timeZone'))
  zones.add(current)
  return [...zones].sort()
}

/** PATCH /api/me (TECHNICAL_DESIGN §9.1). Requires a logged-in user; see the /profile route. */
export function ProfilePage() {
  const { user, updateUser } = useAuth()
  // RequireRole guarantees a user here.
  const me = user as User
  const [saved, setSaved] = useState(false)
  const zones = useMemo(() => timeZoneSuggestions(me.timezone), [me.timezone])
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: me.fullName, timezone: me.timezone },
  })

  const save = useMutation({
    mutationFn: async (body: UpdateProfileRequest) => {
      const { data, error, response } = await api.PATCH('/me', { body })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (updated) => {
      updateUser(updated)
      reset({ fullName: updated.fullName, timezone: updated.timezone })
      setSaved(true)
    },
    onError: (error) => showProblem(error instanceof ApiError ? error.problem : {}),
  })

  function showProblem(problem: ApiProblem) {
    const onForm = (problem.errors ?? []).filter((e) => FIELDS.includes(e.field))
    onForm.forEach((e, i) =>
      setError(
        e.field as FieldName,
        { message: e.message.charAt(0).toUpperCase() + e.message.slice(1) },
        { shouldFocus: i === 0 },
      ),
    )
    if (onForm.length === 0) {
      setError('root', { message: errorMessage(problem.code) })
    }
  }

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Your profile</h1>
      <p className="mt-2 text-sm text-gray-600">
        Signed in as <span className="font-medium">{me.email}</span>
      </p>

      <form
        noValidate
        className="mt-6 space-y-4"
        onSubmit={handleSubmit((values) => {
          setSaved(false)
          save.mutate(values)
        })}
      >
        {errors.root && (
          <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errors.root.message}
          </p>
        )}
        {saved && (
          <p role="status" className="rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900">
            Your profile is saved.
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
          id="timezone"
          label="Time zone"
          list="timezones"
          autoComplete="off"
          hint="Start typing, e.g. Europe/London. Used for times in your emails."
          error={errors.timezone?.message}
          {...register('timezone')}
        />
        <datalist id="timezones">
          {zones.map((zone) => (
            <option key={zone} value={zone} />
          ))}
        </datalist>

        <button
          type="submit"
          disabled={save.isPending}
          className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {save.isPending ? 'Saving…' : 'Save'}
        </button>
      </form>
    </main>
  )
}
