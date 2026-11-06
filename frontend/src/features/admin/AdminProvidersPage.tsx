import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { FormField } from '../../components/FormField'
import { TimeZoneField } from '../../components/TimeZoneField'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

type InviteProviderRequest = components['schemas']['InviteProviderRequest']

const PAGE_SIZE = 20

// Quick client checks; the server validates fully (e.g. the IANA zone).
const schema = z.object({
  fullName: z.string().trim().min(1, "Enter the provider's full name").max(120),
  displayName: z.string().trim().min(1, 'Enter the name customers will see').max(120),
  email: z.email('Enter a valid email address'),
  timezone: z.string().trim().min(1, 'Choose a time zone'),
})

type FormValues = z.infer<typeof schema>
type FieldName = keyof FormValues
const FIELDS: readonly string[] = ['fullName', 'displayName', 'email', 'timezone'] satisfies FieldName[]

/** Admin: provider list and invite form (A1; TECHNICAL_DESIGN §9.1). */
export function AdminProvidersPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [invitedEmail, setInvitedEmail] = useState<string | null>(null)
  const defaults: FormValues = {
    fullName: '',
    displayName: '',
    email: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  }
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: defaults })

  const providers = useQuery({
    queryKey: queryKeys.adminProviders.page(page),
    queryFn: async () => {
      const result = await api.GET('/admin/providers', {
        params: { query: { page, size: PAGE_SIZE } },
      })
      if (!result.response.ok || !result.data) {
        throw new ApiError(result.response.status, result.error)
      }
      return result.data
    },
    placeholderData: keepPreviousData,
  })

  const invite = useMutation({
    mutationFn: async (body: InviteProviderRequest) => {
      const { data, error, response } = await api.POST('/admin/providers', { body })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (provider) => {
      setInvitedEmail(provider.email)
      reset(defaults)
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminProviders.all })
    },
    onError: (error) => showProblem(error instanceof ApiError ? error.problem : {}),
  })

  function showProblem(problem: ApiProblem) {
    if (problem.code === 'EMAIL_TAKEN') {
      setError('email', { message: errorMessage('EMAIL_TAKEN') }, { shouldFocus: true })
      return
    }
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

  const total = providers.data?.total ?? 0
  const lastPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1)

  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-12">
      <h1 className="text-2xl font-semibold">Providers</h1>

      <section aria-labelledby="invite-heading" className="mt-8 max-w-md">
        <h2 id="invite-heading" className="text-lg font-semibold">
          Invite a provider
        </h2>
        <p className="mt-1 text-sm text-gray-600">
          They'll get an email with a link to set their password. The link is valid for 72 hours.
        </p>

        {/* Always in the DOM, so screen readers announce the confirmation when it appears. */}
        <p
          role="status"
          className={
            invitedEmail
              ? 'mt-4 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900'
              : 'sr-only'
          }
        >
          {invitedEmail ? `Invitation sent to ${invitedEmail}.` : ''}
        </p>

        <form
          noValidate
          className="mt-4 space-y-4"
          onSubmit={handleSubmit((values) => {
            setInvitedEmail(null)
            invite.mutate(values)
          })}
        >
          {errors.root && (
            <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
              {errors.root.message}
            </p>
          )}
          <FormField id="fullName" label="Full name" error={errors.fullName?.message} {...register('fullName')} />
          <FormField
            id="displayName"
            label="Display name"
            hint="Shown to customers, e.g. Dr Iyer."
            error={errors.displayName?.message}
            {...register('displayName')}
          />
          <FormField
            id="email"
            label="Email"
            type="email"
            autoComplete="off"
            error={errors.email?.message}
            {...register('email')}
          />
          <TimeZoneField
            id="timezone"
            label="Time zone"
            hint="Where the provider works. Their weekly hours and slots use this zone."
            error={errors.timezone?.message}
            {...register('timezone')}
          />
          <button
            type="submit"
            disabled={invite.isPending}
            className="w-full rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
          >
            {invite.isPending ? 'Sending…' : 'Send invite'}
          </button>
        </form>
      </section>

      <section aria-labelledby="list-heading" className="mt-12">
        <h2 id="list-heading" className="text-lg font-semibold">
          All providers
        </h2>

        {providers.isPending && <p className="mt-4 text-sm text-gray-600">Loading providers…</p>}
        {providers.isError && (
          <p role="alert" className="mt-4 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errorMessage(providers.error instanceof ApiError ? providers.error.problem.code : undefined)}
          </p>
        )}
        {providers.data && providers.data.items.length === 0 && (
          <p className="mt-4 text-sm text-gray-600">No providers yet. Invite the first one above.</p>
        )}
        {providers.data && providers.data.items.length > 0 && (
          <ul aria-label="Providers" className="mt-4 divide-y divide-gray-200 rounded border border-gray-200">
            {providers.data.items.map((provider) => (
              <li key={provider.id} className="flex flex-wrap items-start justify-between gap-2 p-4">
                <div className="min-w-0">
                  <p className="font-medium break-words">{provider.displayName}</p>
                  <p className="text-sm break-all text-gray-600">{provider.email}</p>
                  <p className="text-sm text-gray-600">{provider.timezone}</p>
                </div>
                {/* Text, not just colour, carries the status. */}
                <span
                  className={
                    provider.invitePending
                      ? 'rounded border border-amber-300 bg-amber-50 px-2 py-0.5 text-sm text-amber-900'
                      : 'rounded border border-green-300 bg-green-50 px-2 py-0.5 text-sm text-green-900'
                  }
                >
                  {provider.invitePending ? 'Invite pending' : 'Active'}
                </span>
              </li>
            ))}
          </ul>
        )}

        {total > PAGE_SIZE && (
          <nav aria-label="Provider pages" className="mt-4 flex items-center justify-between text-sm">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
              className="rounded px-2 py-1 text-blue-700 underline disabled:text-gray-400 disabled:no-underline"
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {lastPage + 1}
            </span>
            <button
              type="button"
              disabled={page >= lastPage}
              onClick={() => setPage((p) => p + 1)}
              className="rounded px-2 py-1 text-blue-700 underline disabled:text-gray-400 disabled:no-underline"
            >
              Next
            </button>
          </nav>
        )}
      </section>
    </main>
  )
}
