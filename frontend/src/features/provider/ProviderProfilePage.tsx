import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { FormField } from '../../components/FormField'
import { TimeZoneField } from '../../components/TimeZoneField'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

type ProviderProfileDto = components['schemas']['ProviderProfileDto']

// Quick client checks only; the server validates the zone and the buffer's range and steps.
const schema = z.object({
  displayName: z.string().trim().min(1, 'Enter the name customers see').max(120),
  bio: z.string().trim().max(2000, 'Use at most 2000 characters'),
  timezone: z.string().trim().min(1, 'Choose a time zone'),
  bufferMinutes: z.number({ error: 'Enter the buffer in minutes' }).int('Use whole minutes'),
})

type FormValues = z.infer<typeof schema>
type FieldName = keyof FormValues
const FIELDS: readonly string[] = ['displayName', 'bio', 'timezone', 'bufferMinutes'] satisfies FieldName[]

/** GET/PATCH /api/provider/me (TECHNICAL_DESIGN §9.1): what customers see, plus zone and buffer. */
export function ProviderProfilePage() {
  const profile = useQuery({
    queryKey: queryKeys.providerProfile,
    queryFn: async () => {
      const result = await api.GET('/provider/me')
      if (!result.response.ok || !result.data) {
        throw new ApiError(result.response.status, result.error)
      }
      return result.data
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Your provider profile</h1>
      <p className="mt-2 text-sm text-gray-600">Customers see your display name and bio.</p>
      {profile.isPending && <p className="mt-6 text-sm text-gray-600">Loading your profile…</p>}
      {profile.isError && (
        <p role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {errorMessage(profile.error instanceof ApiError ? profile.error.problem.code : undefined)}
        </p>
      )}
      {profile.data && <ProfileForm profile={profile.data} />}
    </main>
  )
}

function valuesOf(profile: ProviderProfileDto): FormValues {
  return {
    displayName: profile.displayName,
    bio: profile.bio ?? '',
    timezone: profile.timezone,
    bufferMinutes: profile.bufferMinutes,
  }
}

function ProfileForm({ profile }: { profile: ProviderProfileDto }) {
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    reset,
    control,
    formState: { errors, isDirty },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: valuesOf(profile) })
  // Hide the confirmation as soon as there are unsaved edits again.
  const showSaved = saved && !isDirty
  const newZone = useWatch({ control, name: 'timezone' }).trim()
  const zoneChanging = newZone !== '' && newZone !== profile.timezone

  const save = useMutation({
    mutationFn: async (body: FormValues) => {
      const { data, error, response } = await api.PATCH('/provider/me', { body })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.providerProfile, updated)
      reset(valuesOf(updated))
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

  const bioError = errors.bio?.message

  return (
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
      {/* Always in the DOM, so screen readers announce the text when it appears. */}
      <p
        role="status"
        className={showSaved ? 'rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'}
      >
        {showSaved ? 'Your provider profile is saved.' : ''}
      </p>

      <FormField
        id="displayName"
        label="Display name"
        hint="Shown to customers, e.g. Dr Iyer."
        error={errors.displayName?.message}
        {...register('displayName')}
      />
      <div>
        <label htmlFor="bio" className="block text-sm font-medium">
          Bio <span className="font-normal text-gray-600">(optional)</span>
        </label>
        <textarea
          id="bio"
          rows={4}
          aria-invalid={bioError ? true : undefined}
          aria-describedby={bioError ? 'bio-error' : undefined}
          className="mt-1 w-full rounded border border-gray-400 px-3 py-2 focus-visible:outline-2 focus-visible:outline-blue-700 aria-invalid:border-red-600"
          {...register('bio')}
        />
        {bioError && (
          <p id="bio-error" className="mt-1 text-sm text-red-700">
            {bioError}
          </p>
        )}
      </div>
      <TimeZoneField
        id="timezone"
        label="Time zone"
        include={profile.timezone}
        hint="Where you work. Your weekly hours and slots use this zone."
        error={errors.timezone?.message}
        {...register('timezone')}
      />
      {zoneChanging && (
        <p className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          Your weekly hours keep the same local times in {newZone}. For example, 09:00 stays 09:00 there, which
          is a different moment than 09:00 in {profile.timezone}.
        </p>
      )}
      <FormField
        id="bufferMinutes"
        label="Buffer between appointments (minutes)"
        type="number"
        inputMode="numeric"
        min={0}
        max={120}
        step={5}
        hint="0 to 120 minutes, in steps of 5. Applies to new bookings only."
        error={errors.bufferMinutes?.message}
        {...register('bufferMinutes', { valueAsNumber: true })}
      />

      <button
        type="submit"
        disabled={save.isPending}
        className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
      >
        {save.isPending ? 'Saving…' : 'Save'}
      </button>
    </form>
  )
}
