import type { ComponentPropsWithRef } from 'react'

type Props = ComponentPropsWithRef<'input'> & {
  id: string
  label: string
  hint?: string
  error?: string
  /** Ids of more text that describes the field, e.g. a warning shown next to it. */
  describedBy?: string
}

/** A labelled input whose hint and error are announced with it (aria-describedby). */
export function FormField({ id, label, hint, error, describedBy: extra, ...input }: Props) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId, extra].filter(Boolean).join(' ') || undefined

  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className="mt-1 w-full rounded border border-gray-400 px-3 py-2 focus-visible:outline-2 focus-visible:outline-blue-700 aria-invalid:border-red-600"
        {...input}
      />
      {error && (
        <p id={errorId} className="mt-1 text-sm text-red-700">
          {error}
        </p>
      )}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-sm text-gray-600">
          {hint}
        </p>
      )}
    </div>
  )
}
