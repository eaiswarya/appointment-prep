import { useMemo, type ComponentProps } from 'react'
import { FormField } from './FormField'

type Props = Omit<ComponentProps<typeof FormField>, 'list'> & {
  /** A zone to always offer, e.g. the current value, even if this browser doesn't list it. */
  include?: string
}

/** IANA zones this browser knows, plus `include`. Older browsers lack Intl.supportedValuesOf. */
function suggestions(include?: string): string[] {
  const zones = new Set(
    typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [],
  )
  if (include) {
    zones.add(include)
  }
  return [...zones].sort()
}

/**
 * A text field for an IANA time zone with suggestions. Free text is allowed and the server is the
 * authority, because browser lists differ (Chrome knows Asia/Calcutta but not Asia/Kolkata).
 */
export function TimeZoneField({ id, include, ...field }: Props) {
  const zones = useMemo(() => suggestions(include), [include])
  const listId = `${id}-zones`

  return (
    <>
      <FormField id={id} list={listId} autoComplete="off" {...field} />
      <datalist id={listId}>
        {zones.map((zone) => (
          <option key={zone} value={zone} />
        ))}
      </datalist>
    </>
  )
}
