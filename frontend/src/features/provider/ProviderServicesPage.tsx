import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { ApiError, errorMessage } from '../../lib/errors'

type ProviderServicesDto = components['schemas']['ProviderServicesDto']

const UNAVAILABLE = 'One of the services you chose is no longer available. The list is updated; check it and save again.'

/** GET/PUT /api/provider/services (P3; TECHNICAL_DESIGN §9.1): the services this provider offers. */
export function ProviderServicesPage() {
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
      {services.data && services.data.available.length > 0 && <ServicesForm services={services.data} />}
    </main>
  )
}

function ServicesForm({ services }: { services: ProviderServicesDto }) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState(() => new Set(services.offeredServiceIds))
  const [notice, setNotice] = useState('')
  const [problem, setProblem] = useState('')

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
      setSelected(new Set(updated.offeredServiceIds))
      setNotice('Your services are saved.')
    },
    onError: (error) => {
      const fromServer = error instanceof ApiError ? error.problem : {}
      if (fromServer.errors?.some((e) => e.field === 'serviceIds')) {
        setProblem(UNAVAILABLE)
        void queryClient.invalidateQueries({ queryKey: queryKeys.providerServices })
      } else {
        setProblem(errorMessage(fromServer.code))
      }
    },
  })

  function toggle(id: string) {
    setNotice('')
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
    setNotice('')
    setProblem('')
    save.mutate(chosen)
  }

  return (
    <form noValidate className="mt-6 space-y-4" onSubmit={submit}>
      {problem && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {problem}
        </p>
      )}
      {/* Always in the DOM, so screen readers announce the text when it appears. */}
      <p
        role="status"
        className={notice ? 'rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'}
      >
        {notice}
      </p>

      <fieldset>
        <legend className="text-sm font-medium">Services you offer</legend>
        <ul className="mt-2 divide-y divide-gray-200 rounded border border-gray-200">
          {services.available.map((service) => (
            <li key={service.id} className="p-3">
              <label className="flex items-start gap-3">
                <input
                  type="checkbox"
                  checked={selected.has(service.id)}
                  onChange={() => toggle(service.id)}
                  aria-describedby={service.description ? `service-${service.id}-description` : undefined}
                  className="mt-1 h-4 w-4 shrink-0 focus-visible:outline-2 focus-visible:outline-blue-700"
                />
                <span className="min-w-0">
                  <span className="font-medium break-words">{service.name}</span>{' '}
                  <span className="text-sm text-gray-600">({service.durationMinutes} min)</span>
                  {service.description && (
                    <span
                      id={`service-${service.id}-description`}
                      className="block text-sm break-words text-gray-700"
                    >
                      {service.description}
                    </span>
                  )}
                </span>
              </label>
            </li>
          ))}
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
