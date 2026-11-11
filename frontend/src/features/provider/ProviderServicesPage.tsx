import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

type ProviderServicesDto = components['schemas']['ProviderServicesDto']

const UNAVAILABLE = 'One of the services you chose is no longer available. The list is updated; check it and save again.'

/** GET/PUT /api/provider/services (P3; TECHNICAL_DESIGN §9.1): the services this provider offers. */
export function ProviderServicesPage() {
  // Kept here, not in the form: the form is re-created whenever the server's set changes.
  const [notice, setNotice] = useState('')
  const [problem, setProblem] = useState('')

  const services = useQuery({
    queryKey: queryKeys.providerServices,
    queryFn: async () => {
      const result = await api.GET('/provider/services')
      if (!result.response.ok || !result.data) {
        throw new ApiError(result.response.status, result.error)
      }
      return result.data
    },
  })

  return (
    <main className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-2xl font-semibold">Your services</h1>
      <p className="mt-2 text-sm text-gray-600">Customers can book you for the services you offer.</p>
      {problem && (
        <p role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {problem}
        </p>
      )}
      {/* Always in the DOM, so screen readers announce the text when it appears. */}
      <p
        role="status"
        className={notice ? 'mt-6 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'}
      >
        {notice}
      </p>
      {services.isPending && <p className="mt-6 text-sm text-gray-600">Loading services…</p>}
      {services.isError && (
        <p role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {errorMessage(services.error instanceof ApiError ? services.error.problem.code : undefined)}
        </p>
      )}
      {services.data && services.data.available.length === 0 && (
        <p className="mt-6 text-sm text-gray-600">
          There are no services in the catalog yet. Ask your admin to add some.
        </p>
      )}
      {services.data && services.data.available.length > 0 && (
        <ServicesForm
          // A new offered set from the server (a save, or a service reactivated meanwhile) starts the
          // form afresh, so a save never drops an offered service the form didn't know about.
          key={[...services.data.offeredServiceIds].sort().join()}
          services={services.data}
          onChange={() => {
            setNotice('')
            setProblem('')
          }}
          onSaved={() => setNotice('Your services are saved.')}
          onProblem={setProblem}
        />
      )}
    </main>
  )
}

type FormProps = {
  services: ProviderServicesDto
  onChange: () => void
  onSaved: () => void
  onProblem: (message: string) => void
}

function ServicesForm({ services, onChange, onSaved, onProblem }: FormProps) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState(() => new Set(services.offeredServiceIds))

  // Keep only choices that are still on offer when the list changes (e.g. after a refetch).
  const available = new Set(services.available.map((s) => s.id))
  const chosen = [...selected].filter((id) => available.has(id))

  const save = useMutation({
    mutationFn: async (serviceIds: string[]) => {
      const { data, error, response } = await api.PUT('/provider/services', { body: { serviceIds } })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.providerServices, updated)
      onSaved()
    },
    onError: (error) => {
      const fromServer: ApiProblem = error instanceof ApiError ? error.problem : {}
      // The server's "must only contain services that exist and are active".
      if (fromServer.errors?.some((e) => e.field === 'serviceIds' && /active/.test(e.message))) {
        onProblem(UNAVAILABLE)
        void queryClient.invalidateQueries({ queryKey: queryKeys.providerServices })
      } else {
        onProblem(errorMessage(fromServer.code))
      }
    },
  })

  function toggle(id: string) {
    onChange()
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onChange()
    save.mutate(chosen)
  }

  return (
    <form noValidate className="mt-6 space-y-4" onSubmit={submit}>
      <fieldset>
        <legend className="text-sm font-medium">Services you offer</legend>
        <ul className="mt-2 divide-y divide-gray-200 rounded border border-gray-200">
          {services.available.map((service) => {
            const descriptionId = `service-${service.id}-description`
            return (
              <li key={service.id} className="p-3">
                <label className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    checked={selected.has(service.id)}
                    onChange={() => toggle(service.id)}
                    aria-describedby={service.description ? descriptionId : undefined}
                    className="mt-1 h-4 w-4 shrink-0 focus-visible:outline-2 focus-visible:outline-blue-700"
                  />
                  <span className="min-w-0">
                    <span className="font-medium break-words">{service.name}</span>{' '}
                    <span className="text-sm text-gray-600">({service.durationMinutes} min)</span>
                  </span>
                </label>
                {/* Outside the label, so it's read once, as the description. */}
                {service.description && (
                  <p id={descriptionId} className="mt-1 pl-7 text-sm break-words text-gray-700">
                    {service.description}
                  </p>
                )}
              </li>
            )
          })}
        </ul>
      </fieldset>

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
