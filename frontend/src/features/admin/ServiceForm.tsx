import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { FormField } from '../../components/FormField'
import { ApiError, errorMessage, type ApiProblem } from '../../lib/errors'

// Quick client checks only; the server owns the duration rule (INVALID_DURATION).
const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(120, 'Use at most 120 characters'),
  durationMinutes: z.number({ error: 'Enter the length in minutes' }).int('Use whole minutes'),
  description: z.string().trim().max(2000, 'Use at most 2000 characters'),
})

export type ServiceValues = z.infer<typeof schema>
type FieldName = keyof ServiceValues
const FIELDS: readonly string[] = ['name', 'durationMinutes', 'description'] satisfies FieldName[]

const EMPTY: ServiceValues = { name: '', durationMinutes: Number.NaN, description: '' }

type Props = {
  /** Prefix for input ids, so several forms can be on the page at once. */
  id: string
  label: string
  initial?: ServiceValues
  submitLabel: string
  pendingLabel: string
  /** Rejects with ApiError on failure; the form then shows the problem on the right field. */
  onSubmit: (values: ServiceValues) => Promise<unknown>
  onCancel?: () => void
}

/** Name, duration and description of a catalog service, for both adding and editing. */
export function ServiceForm({ id, label, initial, submitLabel, pendingLabel, onSubmit, onCancel }: Props) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ServiceValues>({ resolver: zodResolver(schema), defaultValues: initial ?? EMPTY })

  function showProblem(problem: ApiProblem) {
    if (problem.code === 'INVALID_DURATION') {
      setError('durationMinutes', { message: errorMessage('INVALID_DURATION') }, { shouldFocus: true })
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

  const descriptionError = errors.description?.message

  return (
    <form
      aria-label={label}
      noValidate
      className="space-y-4"
      onSubmit={handleSubmit(async (values) => {
        try {
          await onSubmit(values)
          if (!initial) {
            reset(EMPTY)
          }
        } catch (error) {
          showProblem(error instanceof ApiError ? error.problem : {})
        }
      })}
    >
      {errors.root && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          {errors.root.message}
        </p>
      )}
      <FormField id={`${id}-name`} label="Name" error={errors.name?.message} {...register('name')} />
      <FormField
        id={`${id}-duration`}
        label="Duration (minutes)"
        type="number"
        inputMode="numeric"
        min={5}
        max={480}
        step={5}
        hint="Between 5 and 480 minutes, in steps of 5."
        error={errors.durationMinutes?.message}
        {...register('durationMinutes', { valueAsNumber: true })}
      />
      <div>
        <label htmlFor={`${id}-description`} className="block text-sm font-medium">
          Description <span className="font-normal text-gray-600">(optional)</span>
        </label>
        <textarea
          id={`${id}-description`}
          rows={3}
          aria-invalid={descriptionError ? true : undefined}
          aria-describedby={descriptionError ? `${id}-description-error` : undefined}
          className="mt-1 w-full rounded border border-gray-400 px-3 py-2 focus-visible:outline-2 focus-visible:outline-blue-700 aria-invalid:border-red-600"
          {...register('description')}
        />
        {descriptionError && (
          <p id={`${id}-description-error`} className="mt-1 text-sm text-red-700">
            {descriptionError}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded bg-blue-700 px-4 py-2 font-medium text-white hover:bg-blue-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700 disabled:opacity-60"
        >
          {isSubmitting ? pendingLabel : submitLabel}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="rounded border border-gray-400 px-4 py-2 font-medium hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
