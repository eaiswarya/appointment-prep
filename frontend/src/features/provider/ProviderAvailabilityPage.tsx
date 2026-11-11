import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { components } from '../../api/schema'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

type AvailabilityDto = components['schemas']['AvailabilityDto']
type AvailabilityRuleDto = components['schemas']['AvailabilityRuleDto']

// ISO days, as the API numbers them.
const DAYS = [
  [1, 'Monday'],
  [2, 'Tuesday'],
  [3, 'Wednesday'],
  [4, 'Thursday'],
  [5, 'Friday'],
  [6, 'Saturday'],
  [7, 'Sunday'],
] as const

/** One editable range. `key` stays stable while times change, for React and error mapping. */
type Row = { key: string; day: number; startTime: string; endTime: string }
/** Messages for a row: on one time, or on the range as a whole (e.g. an overlap). */
type RowErrors = { startTime?: string; endTime?: string; range?: string }

/** GET/PUT /api/provider/availability (P1, FR-8): weekly hours in the provider's own zone. */
export function ProviderAvailabilityPage() {
  // Kept here, not in the editor: the editor is re-created when the saved rules change.
  const [notice, setNotice] = useState('')
  const [problem, setProblem] = useState('')
  const status = useRef<HTMLParagraphElement>(null)

  // A save rebuilds the editor (and its Save button), so take focus to the confirmation instead.
  useEffect(() => {
    if (notice) {
      status.current?.focus()
    }
  }, [notice])

  const availability = useQuery({
    queryKey: queryKeys.providerAvailability,
    queryFn: async () => {
      const result = await api.GET('/provider/availability')
      if (!result.response.ok || !result.data) {
        throw new ApiError(result.response.status, result.error)
      }
      return result.data
    },
  })

  return (
    <main className="mx-auto w-full max-w-xl px-4 py-12">
      <h1 className="text-2xl font-semibold">Weekly hours</h1>
      {availability.data && (
        <p className="mt-2 text-sm text-gray-600">
          Times are in {availability.data.timezone}, your provider time zone.{' '}
          <Link to="/provider/profile" className="text-blue-700 underline">
            Change it in your profile
          </Link>
          . Each range ends on the same day, by 23:55 at the latest.
        </p>
      )}
      {problem && (
        <p role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {problem}
        </p>
      )}
      {/* Always in the DOM, so screen readers announce the text when it appears. */}
      <p
        ref={status}
        role="status"
        tabIndex={-1}
        className={notice ? 'mt-6 rounded border border-green-300 bg-green-50 p-3 text-sm text-green-900' : 'sr-only'}
      >
        {notice}
      </p>
      {availability.isPending && <p className="mt-6 text-sm text-gray-600">Loading your hours…</p>}
      {availability.isError && (
        <p role="alert" className="mt-6 rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {errorMessage(availability.error instanceof ApiError ? availability.error.problem.code : undefined)}
        </p>
      )}
      {availability.data && (
        <Editor
          key={JSON.stringify(availability.data.rules)}
          availability={availability.data}
          onChange={() => {
            setNotice('')
            setProblem('')
          }}
          onSaved={() => setNotice('Your weekly hours are saved.')}
          onProblem={setProblem}
        />
      )}
    </main>
  )
}

type EditorProps = {
  availability: AvailabilityDto
  onChange: () => void
  onSaved: () => void
  onProblem: (message: string) => void
}

function Editor({ availability, onChange, onSaved, onProblem }: EditorProps) {
  const queryClient = useQueryClient()
  const [rows, setRows] = useState<Row[]>(() =>
    availability.rules.map((rule, i) => ({ key: `range-${i}`, day: rule.dayOfWeek, ...times(rule) })),
  )
  // Keys for ranges added later; only read in event handlers.
  const nextKey = useRef(availability.rules.length)
  const newKey = () => `range-${nextKey.current++}`
  const [errors, setErrors] = useState<Record<string, RowErrors>>({})
  // An element to focus once the change that set it has rendered: a new range's start, a day's
  // Add button, or the first range with a problem.
  const focusAfterRender = useRef<string | null>(null)
  const setFocusId = (id: string) => {
    focusAfterRender.current = id
  }

  useEffect(() => {
    if (focusAfterRender.current) {
      document.getElementById(focusAfterRender.current)?.focus()
      focusAfterRender.current = null
    }
  })

  // The request lists ranges day by day; remember which row each index came from.
  const ordered = DAYS.flatMap(([day]) => rows.filter((row) => row.day === day))

  const save = useMutation({
    mutationFn: async (rules: AvailabilityRuleDto[]) => {
      const { data, error, response } = await api.PUT('/provider/availability', { body: { rules } })
      if (error !== undefined || !response.ok || !data) {
        throw new ApiError(response.status, error)
      }
      return data
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.providerAvailability, saved)
      onSaved()
    },
    onError: (error) => showProblem(error instanceof ApiError ? error.problem : {}),
  })

  function showProblem(problem: ApiProblem) {
    const mapped: Record<string, RowErrors> = {}
    let unmapped = false
    for (const { field, message } of problem.errors ?? []) {
      const match = /^rules\[(\d+)\](?:\.(\w+))?$/.exec(field)
      const row = match ? ordered[Number(match[1])] : undefined
      if (!row) {
        unmapped = true
        continue
      }
      const text = message.charAt(0).toUpperCase() + message.slice(1)
      const part = match?.[2] === 'startTime' || match?.[2] === 'endTime' ? match[2] : 'range'
      const earlier = mapped[row.key]?.[part]
      mapped[row.key] = { ...mapped[row.key], [part]: earlier ? `${earlier}; ${text}` : text }
    }
    setErrors(mapped)
    if (unmapped || Object.keys(mapped).length === 0) {
      onProblem(errorMessage(problem.code))
    }
    const first = ordered.find((row) => mapped[row.key])
    if (first) {
      setFocusId(mapped[first.key].endTime && !mapped[first.key].startTime ? `${first.key}-end` : `${first.key}-start`)
    }
  }

  function update(key: string, change: Partial<Row>) {
    onChange()
    setErrors((current) => {
      const rest = { ...current }
      delete rest[key]
      return rest
    })
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...change } : row)))
  }

  function add(day: number) {
    onChange()
    const last = rows.filter((row) => row.day === day).at(-1)
    const row: Row = last
      ? { key: newKey(), day, startTime: last.endTime, endTime: '' }
      : { key: newKey(), day, startTime: '09:00', endTime: '17:00' }
    setRows((current) => [...current, row])
    setFocusId(`${row.key}-start`)
  }

  function remove(row: Row) {
    onChange()
    setRows((current) => current.filter((r) => r.key !== row.key))
    setFocusId(`add-${row.day}`)
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onChange()
    const missing: Record<string, RowErrors> = {}
    for (const row of ordered) {
      if (!row.startTime || !row.endTime) {
        missing[row.key] = {
          ...(row.startTime ? {} : { startTime: 'Enter both times' }),
          ...(row.endTime ? {} : { endTime: 'Enter both times' }),
        }
      }
    }
    if (Object.keys(missing).length > 0) {
      setErrors(missing)
      const first = ordered.find((row) => missing[row.key])
      if (first) {
        setFocusId(missing[first.key].startTime ? `${first.key}-start` : `${first.key}-end`)
      }
      return
    }
    setErrors({})
    save.mutate(ordered.map((row) => ({ dayOfWeek: row.day, startTime: row.startTime, endTime: row.endTime })))
  }

  return (
    <form noValidate className="mt-6 space-y-4" onSubmit={submit}>
      {DAYS.map(([day, name]) => {
        const dayRows = rows.filter((row) => row.day === day)
        return (
          <fieldset key={day} className="rounded border border-gray-200 p-3">
            <legend className="px-1 font-medium">{name}</legend>
            {dayRows.length === 0 ? (
              <p className="text-sm text-gray-600">Closed</p>
            ) : (
              <ul className="space-y-3">
                {dayRows.map((row) => (
                  <RangeRow
                    key={row.key}
                    row={row}
                    dayName={name}
                    errors={errors[row.key]}
                    onChange={(change) => update(row.key, change)}
                    onRemove={() => remove(row)}
                  />
                ))}
              </ul>
            )}
            <button
              type="button"
              id={`add-${day}`}
              aria-label={`Add hours on ${name}`}
              onClick={() => add(day)}
              className="mt-2 rounded px-2 py-1 text-sm text-blue-700 underline focus-visible:outline-2 focus-visible:outline-blue-700"
            >
              Add hours
            </button>
          </fieldset>
        )
      })}

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

type RangeRowProps = {
  row: Row
  dayName: string
  errors?: RowErrors
  onChange: (change: Partial<Row>) => void
  onRemove: () => void
}

function RangeRow({ row, dayName, errors, onChange, onRemove }: RangeRowProps) {
  const rangeError = errors?.range
  const ids = { start: `${row.key}-start`, end: `${row.key}-end`, range: `${row.key}-error` }
  const describe = (own?: string, ownId?: string) =>
    [rangeError ? ids.range : null, own ? ownId : null].filter(Boolean).join(' ') || undefined
  const inputClass =
    'mt-1 w-full rounded border border-gray-400 px-3 py-2 focus-visible:outline-2 focus-visible:outline-blue-700 aria-invalid:border-red-600'

  return (
    <li>
      <div className="flex flex-wrap items-end gap-3">
        <div className="min-w-[7rem] flex-1">
          <label htmlFor={ids.start} className="block text-sm">
            From
          </label>
          <input
            id={ids.start}
            type="time"
            step={300}
            value={row.startTime}
            onChange={(event) => onChange({ startTime: event.target.value })}
            aria-invalid={errors?.startTime || rangeError ? true : undefined}
            aria-describedby={describe(errors?.startTime, `${ids.start}-error`)}
            className={inputClass}
          />
        </div>
        <div className="min-w-[7rem] flex-1">
          <label htmlFor={ids.end} className="block text-sm">
            To
          </label>
          <input
            id={ids.end}
            type="time"
            step={300}
            value={row.endTime}
            onChange={(event) => onChange({ endTime: event.target.value })}
            aria-invalid={errors?.endTime || rangeError ? true : undefined}
            aria-describedby={describe(errors?.endTime, `${ids.end}-error`)}
            className={inputClass}
          />
        </div>
        <button
          type="button"
          aria-label={`Remove ${row.startTime || '…'}–${row.endTime || '…'} on ${dayName}`}
          onClick={onRemove}
          className="rounded px-2 py-2 text-sm text-blue-700 underline focus-visible:outline-2 focus-visible:outline-blue-700"
        >
          Remove
        </button>
      </div>
      {errors?.startTime && (
        <p id={`${ids.start}-error`} className="mt-1 text-sm text-red-700">
          From: {errors.startTime}
        </p>
      )}
      {errors?.endTime && (
        <p id={`${ids.end}-error`} className="mt-1 text-sm text-red-700">
          To: {errors.endTime}
        </p>
      )}
      {rangeError && (
        <p id={ids.range} className="mt-1 text-sm text-red-700">
          {rangeError}
        </p>
      )}
    </li>
  )
}

function times(rule: AvailabilityRuleDto) {
  return { startTime: rule.startTime, endTime: rule.endTime }
}
