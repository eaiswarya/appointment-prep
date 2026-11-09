import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { ApiError, errorMessage } from '../../lib/errors'
import { ServiceForm, type ServiceValues } from './ServiceForm'

type ServiceDto = components['schemas']['ServiceDto']
type UpdateServiceRequest = components['schemas']['UpdateServiceRequest']

const PAGE_SIZE = 20

/** Admin: the service catalog (A2; TECHNICAL_DESIGN §9.1). Services are deactivated, never deleted. */
export function AdminServicesPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  // After an edit closes, focus goes back to that service's Edit button.
  const returnFocusTo = useRef<string | null>(null)

  useEffect(() => {
    if (editingId === null && returnFocusTo.current) {
      document.getElementById(`edit-${returnFocusTo.current}`)?.focus()
      returnFocusTo.current = null
    }
  }, [editingId])

  const services = useQuery({
    queryKey: queryKeys.adminServices.page(page),
    queryFn: async () => {
      const result = await api.GET('/admin/services', {
        params: { query: { page, size: PAGE_SIZE } },
      })
      if (!result.response.ok || !result.data) {
        throw new ApiError(result.response.status, result.error)
      }
      return result.data
    },
    placeholderData: keepPreviousData,
  })

  function refresh() {
    // The public catalog changes too (provider services, directory filter).
    void queryClient.invalidateQueries({ queryKey: queryKeys.adminServices.all })
    void queryClient.invalidateQueries({ queryKey: queryKeys.services })
  }

  const create = useMutation({
    mutationFn: async (values: ServiceValues) => {
      const { data, error, response } = await api.POST('/admin/services', { body: values })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (service) => {
      setNotice(`${service.name} was added.`)
      refresh()
    },
  })

  const update = useMutation({
    mutationFn: async ({ id, body }: { id: string; body: UpdateServiceRequest }) => {
      const { data, error, response } = await api.PATCH('/admin/services/{id}', {
        params: { path: { id } },
        body,
      })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (service, { body }) => {
      if (body.active === undefined) {
        setNotice(`${service.name} was saved.`)
      } else {
        setNotice(service.active ? `${service.name} was reactivated.` : `${service.name} was deactivated.`)
      }
      refresh()
    },
  })

  function closeEdit(id: string) {
    returnFocusTo.current = id
    setEditingId(null)
  }

  const total = services.data?.total ?? 0
  const lastPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1)
  const toggleError = update.isError && update.variables?.body.active !== undefined ? update.error : null

  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-12">
      <h1 className="text-2xl font-semibold">Services</h1>

      {/* Always in the DOM, so screen readers announce changes when they happen. */}
      <p
        role="status"
        className={notice ? 'mt-4 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'}
      >
        {notice}
      </p>

      <section aria-labelledby="add-heading" className="mt-8 max-w-md">
        <h2 id="add-heading" className="text-lg font-semibold">
          Add a service
        </h2>
        <p className="mt-1 mb-4 text-sm text-gray-600">Every provider who offers it uses the same length.</p>
        <ServiceForm
          id="new"
          label="Add a service"
          submitLabel="Add service"
          pendingLabel="Adding…"
          onSubmit={(values) => {
            setNotice('')
            return create.mutateAsync(values)
          }}
        />
      </section>

      <section aria-labelledby="list-heading" className="mt-12">
        <h2 id="list-heading" className="text-lg font-semibold">
          All services
        </h2>

        {toggleError && (
          <p role="alert" className="mt-4 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errorMessage(toggleError instanceof ApiError ? toggleError.problem.code : undefined)}
          </p>
        )}
        {services.isPending && <p className="mt-4 text-sm text-gray-600">Loading services…</p>}
        {services.isError && (
          <p role="alert" className="mt-4 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
            {errorMessage(services.error instanceof ApiError ? services.error.problem.code : undefined)}
          </p>
        )}
        {services.data && services.data.items.length === 0 && (
          <p className="mt-4 text-sm text-gray-600">No services yet. Add the first one above.</p>
        )}
        {services.data && services.data.items.length > 0 && (
          <ul aria-label="Services" className="mt-4 divide-y divide-gray-200 rounded border border-gray-200">
            {services.data.items.map((service) => (
              <li key={service.id} className="p-4">
                {editingId === service.id ? (
                  <ServiceForm
                    id={`edit-form-${service.id}`}
                    label={`Edit ${service.name}`}
                    initial={valuesOf(service)}
                    submitLabel="Save"
                    pendingLabel="Saving…"
                    onSubmit={async (values) => {
                      setNotice('')
                      await update.mutateAsync({ id: service.id, body: values })
                      closeEdit(service.id)
                    }}
                    onCancel={() => closeEdit(service.id)}
                  />
                ) : (
                  <ServiceRow
                    service={service}
                    busy={update.isPending}
                    onEdit={() => setEditingId(service.id)}
                    onToggle={() => {
                      setNotice('')
                      update.mutate({ id: service.id, body: { active: !service.active } })
                    }}
                  />
                )}
              </li>
            ))}
          </ul>
        )}

        {total > PAGE_SIZE && (
          <nav aria-label="Service pages" className="mt-4 flex items-center justify-between text-sm">
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

function valuesOf(service: ServiceDto): ServiceValues {
  return {
    name: service.name,
    durationMinutes: service.durationMinutes,
    description: service.description ?? '',
  }
}

type RowProps = { service: ServiceDto; busy: boolean; onEdit: () => void; onToggle: () => void }

function ServiceRow({ service, busy, onEdit, onToggle }: RowProps) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="font-medium break-words">{service.name}</p>
        <p className="text-sm text-gray-600">{service.durationMinutes} min</p>
        {service.description && <p className="mt-1 text-sm break-words text-gray-700">{service.description}</p>}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {/* Text, not just colour, carries the status. */}
        <span
          className={
            service.active
              ? 'rounded border border-green-300 bg-green-50 px-2 py-0.5 text-sm text-green-900'
              : 'rounded border border-gray-300 bg-gray-50 px-2 py-0.5 text-sm text-gray-700'
          }
        >
          {service.active ? 'Active' : 'Inactive'}
        </span>
        <button
          type="button"
          id={`edit-${service.id}`}
          aria-label={`Edit ${service.name}`}
          onClick={onEdit}
          className="rounded px-2 py-1 text-sm text-blue-700 underline focus-visible:outline-2 focus-visible:outline-blue-700"
        >
          Edit
        </button>
        <button
          type="button"
          aria-label={`${service.active ? 'Deactivate' : 'Reactivate'} ${service.name}`}
          disabled={busy}
          onClick={onToggle}
          className="rounded px-2 py-1 text-sm text-blue-700 underline focus-visible:outline-2 focus-visible:outline-blue-700 disabled:text-gray-400"
        >
          {service.active ? 'Deactivate' : 'Reactivate'}
        </button>
      </div>
    </div>
  )
}
