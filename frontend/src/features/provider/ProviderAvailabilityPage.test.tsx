import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { problem, provider, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

type AvailabilityDto = components['schemas']['AvailabilityDto']

const weekdays: AvailabilityDto = {
  timezone: 'Asia/Kolkata',
  rules: [
    { dayOfWeek: 1, startTime: '09:00', endTime: '12:00' },
    { dayOfWeek: 1, startTime: '13:00', endTime: '17:00' },
    { dayOfWeek: 3, startTime: '10:00', endTime: '16:00' },
  ],
}

function availabilityIs(current: () => AvailabilityDto) {
  return http.get('*/api/provider/availability', () => HttpResponse.json(current()))
}

/** PUT that records the body and echoes it back as saved. */
function savesTo(sink: { body?: unknown }) {
  return http.put('*/api/provider/availability', async ({ request }) => {
    const body = (await request.json()) as { rules: AvailabilityDto['rules'] }
    sink.body = body
    return HttpResponse.json({ timezone: 'Asia/Kolkata', rules: body.rules })
  })
}

async function day(name: string) {
  return screen.findByRole('group', { name })
}

describe('ProviderAvailabilityPage', () => {
  it('shows the weekly hours by day, in the provider zone', async () => {
    server.use(...sessionExists(provider), availabilityIs(() => weekdays))

    renderRoute('/provider/availability')

    const monday = await day('Monday')
    expect(within(monday).getAllByLabelText('From')).toHaveLength(2)
    expect(within(monday).getAllByLabelText('From')[0]).toHaveValue('09:00')
    expect(within(monday).getAllByLabelText('To')[1]).toHaveValue('17:00')
    expect(within(await day('Tuesday')).getByText('Closed')).toBeInTheDocument()
    expect(within(await day('Wednesday')).getByLabelText('From')).toHaveValue('10:00')
    expect(screen.getByText(/times are in asia\/kolkata/i)).toBeInTheDocument()
  })

  it('adds hours on a closed day, puts focus in them, and saves the week in day order', async () => {
    const sink: { body?: unknown } = {}
    server.use(...sessionExists(provider), availabilityIs(() => weekdays), savesTo(sink))
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    const saturday = await day('Saturday')
    await user.click(within(saturday).getByRole('button', { name: 'Add hours on Saturday' }))
    const from = within(saturday).getByLabelText('From')
    expect(from).toHaveFocus()
    expect(from).toHaveValue('09:00')
    const to = within(saturday).getByLabelText('To')
    await user.clear(to)
    await user.type(to, '13:00')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sink.body).toEqual({
      rules: [
        { dayOfWeek: 1, startTime: '09:00', endTime: '12:00' },
        { dayOfWeek: 1, startTime: '13:00', endTime: '17:00' },
        { dayOfWeek: 3, startTime: '10:00', endTime: '16:00' },
        { dayOfWeek: 6, startTime: '09:00', endTime: '13:00' },
      ],
    })
  })

  it('removes hours, moving focus to the day', async () => {
    const sink: { body?: unknown } = {}
    server.use(...sessionExists(provider), availabilityIs(() => weekdays), savesTo(sink))
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    const wednesday = await day('Wednesday')
    await user.click(within(wednesday).getByRole('button', { name: 'Remove 10:00–16:00 on Wednesday' }))
    expect(within(wednesday).getByText('Closed')).toBeInTheDocument()
    expect(within(wednesday).getByRole('button', { name: 'Add hours on Wednesday' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sink.body).toEqual({ rules: weekdays.rules.slice(0, 2) })
  })

  it('shows an overlap next to the range the server named', async () => {
    server.use(
      ...sessionExists(provider),
      availabilityIs(() => weekdays),
      http.put('*/api/provider/availability', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'rules[1]', message: 'overlaps 09:00–12:00 on the same day' }],
        }),
      ),
    )
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Save' }))

    const monday = await day('Monday')
    const [firstFrom, secondFrom] = within(monday).getAllByLabelText('From')
    await waitFor(() => expect(secondFrom).toHaveAttribute('aria-invalid', 'true'))
    expect(secondFrom).toHaveAccessibleDescription(/overlaps 09:00–12:00/i)
    expect(within(monday).getAllByLabelText('To')[1]).toHaveAttribute('aria-invalid', 'true')
    expect(firstFrom).not.toHaveAttribute('aria-invalid')
  })

  it('shows a field error on the exact time the server named', async () => {
    server.use(
      ...sessionExists(provider),
      availabilityIs(() => weekdays),
      http.put('*/api/provider/availability', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'rules[2].endTime', message: 'must be on a 5-minute step, e.g. 17:00 or 17:05' }],
        }),
      ),
    )
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Save' }))

    const wednesday = await day('Wednesday')
    const to = within(wednesday).getByLabelText('To')
    await waitFor(() => expect(to).toHaveAttribute('aria-invalid', 'true'))
    expect(to).toHaveAccessibleDescription(/5-minute step/i)
    expect(within(wednesday).getByLabelText('From')).not.toHaveAttribute('aria-invalid')
  })

  it('asks for both times before sending anything', async () => {
    let sent = false
    server.use(
      ...sessionExists(provider),
      availabilityIs(() => weekdays),
      http.put('*/api/provider/availability', () => {
        sent = true
        return HttpResponse.json(weekdays)
      }),
    )
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    const wednesday = await day('Wednesday')
    await user.clear(within(wednesday).getByLabelText('To'))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(within(wednesday).getByLabelText('To')).toHaveAttribute('aria-invalid', 'true')
    expect(within(wednesday).getByLabelText('To')).toHaveAccessibleDescription(/enter both times/i)
    expect(sent).toBe(false)
  })

  it('can be used with the keyboard alone', async () => {
    const sink: { body?: unknown } = {}
    server.use(
      ...sessionExists(provider),
      availabilityIs(() => ({ timezone: 'Asia/Kolkata', rules: [] })),
      savesTo(sink),
    )
    renderRoute('/provider/availability')
    const user = userEvent.setup()

    const add = within(await day('Monday')).getByRole('button', { name: 'Add hours on Monday' })
    add.focus()
    await user.keyboard('{Enter}')
    expect(within(await day('Monday')).getByLabelText('From')).toHaveFocus()
    screen.getByRole('button', { name: 'Save' }).focus()
    await user.keyboard('{Enter}')

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sink.body).toEqual({ rules: [{ dayOfWeek: 1, startTime: '09:00', endTime: '17:00' }] })
  })

  it('is linked from the provider area', async () => {
    server.use(...sessionExists(provider), availabilityIs(() => weekdays))
    const { router } = renderRoute('/provider')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Weekly hours' }))

    expect(router.state.location.pathname).toBe('/provider/availability')
  })
})
