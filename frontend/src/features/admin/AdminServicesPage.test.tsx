import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { ERROR_MESSAGES } from '../../lib/errors'
import { admin, problem, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

type ServiceDto = components['schemas']['ServiceDto']
type Page = components['schemas']['PageResponseServiceDto']

const consultation: ServiceDto = {
  id: '0190b7a0-0000-7000-8000-0000000000c1',
  name: 'Initial consultation',
  description: 'First visit',
  durationMinutes: 30,
  active: true,
}
const followUp: ServiceDto = {
  id: '0190b7a0-0000-7000-8000-0000000000c2',
  name: 'Follow-up',
  durationMinutes: 15,
  active: false,
}

/** GET /admin/services that returns whatever `current()` holds, so mutations can change it. */
function listOf(current: () => ServiceDto[]) {
  return http.get('*/api/admin/services', () => {
    const items = current()
    const page: Page = { items, page: 0, size: 20, total: items.length }
    return HttpResponse.json(page)
  })
}

async function servicesList() {
  return screen.findByRole('list', { name: /services/i })
}

describe('AdminServicesPage', () => {
  it('lists services with their duration and status', async () => {
    server.use(...sessionExists(admin), listOf(() => [consultation, followUp]))

    renderRoute('/admin/services')

    const items = within(await servicesList()).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Initial consultation')
    expect(items[0]).toHaveTextContent('30 min')
    expect(items[0]).toHaveTextContent('Active')
    expect(within(items[0]).getByRole('button', { name: 'Deactivate Initial consultation' })).toBeVisible()
    expect(items[1]).toHaveTextContent('Inactive')
    expect(within(items[1]).getByRole('button', { name: 'Reactivate Follow-up' })).toBeVisible()
  })

  it('shows an empty state', async () => {
    server.use(...sessionExists(admin), listOf(() => []))

    renderRoute('/admin/services')

    expect(await screen.findByText(/no services yet/i)).toBeInTheDocument()
  })

  it('adds a service and refreshes the list', async () => {
    let services: ServiceDto[] = []
    let sent: unknown
    server.use(
      ...sessionExists(admin),
      listOf(() => services),
      http.post('*/api/admin/services', async ({ request }) => {
        sent = await request.json()
        services = [consultation]
        return HttpResponse.json(consultation, { status: 201 })
      }),
    )
    renderRoute('/admin/services')
    const form = await screen.findByRole('form', { name: /add a service/i })
    const user = userEvent.setup()

    await user.type(within(form).getByLabelText('Name'), 'Initial consultation')
    await user.type(within(form).getByLabelText(/duration/i), '30')
    await user.type(within(form).getByLabelText(/description/i), 'First visit')
    await user.click(within(form).getByRole('button', { name: /add service/i }))

    expect(await screen.findByRole('status')).toHaveTextContent('Initial consultation was added.')
    expect(sent).toEqual({ name: 'Initial consultation', durationMinutes: 30, description: 'First visit' })
    expect(within(await servicesList()).getByRole('listitem')).toHaveTextContent('Initial consultation')
    expect(within(form).getByLabelText('Name')).toHaveValue('')
  })

  it('shows INVALID_DURATION on the duration field', async () => {
    server.use(
      ...sessionExists(admin),
      listOf(() => []),
      http.post('*/api/admin/services', () => problem(400, { code: 'INVALID_DURATION' })),
    )
    renderRoute('/admin/services')
    const form = await screen.findByRole('form', { name: /add a service/i })
    const user = userEvent.setup()

    await user.type(within(form).getByLabelText('Name'), 'Odd')
    await user.type(within(form).getByLabelText(/duration/i), '7')
    await user.click(within(form).getByRole('button', { name: /add service/i }))

    const duration = within(form).getByLabelText(/duration/i)
    await waitFor(() => expect(duration).toHaveAttribute('aria-invalid', 'true'))
    expect(duration).toHaveAccessibleDescription(ERROR_MESSAGES.INVALID_DURATION)
  })

  it('shows a server field error next to the name', async () => {
    server.use(
      ...sessionExists(admin),
      listOf(() => []),
      http.post('*/api/admin/services', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'name', message: 'must not contain control characters' }],
        }),
      ),
    )
    renderRoute('/admin/services')
    const form = await screen.findByRole('form', { name: /add a service/i })
    const user = userEvent.setup()

    await user.type(within(form).getByLabelText('Name'), 'Odd')
    await user.type(within(form).getByLabelText(/duration/i), '30')
    await user.click(within(form).getByRole('button', { name: /add service/i }))

    const name = within(form).getByLabelText('Name')
    await waitFor(() => expect(name).toHaveAttribute('aria-invalid', 'true'))
    expect(name).toHaveAccessibleDescription(/control characters/i)
  })

  it('checks the form before sending anything', async () => {
    server.use(...sessionExists(admin), listOf(() => []))
    renderRoute('/admin/services')
    const form = await screen.findByRole('form', { name: /add a service/i })
    const user = userEvent.setup()

    await user.click(within(form).getByRole('button', { name: /add service/i }))

    expect(within(form).getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    expect(within(form).getByLabelText(/duration/i)).toHaveAttribute('aria-invalid', 'true')
  })

  it('edits a service in place', async () => {
    let services = [consultation]
    let sent: unknown
    server.use(
      ...sessionExists(admin),
      listOf(() => services),
      http.patch('*/api/admin/services/:id', async ({ request, params }) => {
        sent = { id: params.id, body: await request.json() }
        services = [{ ...consultation, durationMinutes: 45 }]
        return HttpResponse.json(services[0])
      }),
    )
    renderRoute('/admin/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit Initial consultation' }))
    const form = screen.getByRole('form', { name: /edit initial consultation/i })
    // Focus moves into the form that replaced the Edit button.
    await waitFor(() => expect(within(form).getByLabelText('Name')).toHaveFocus())
    const duration = within(form).getByLabelText(/duration/i)
    expect(duration).toHaveValue(30)
    await user.clear(duration)
    await user.type(duration, '45')
    await user.click(within(form).getByRole('button', { name: /save/i }))

    await waitFor(() => expect(screen.queryByRole('form', { name: /edit/i })).not.toBeInTheDocument())
    expect(sent).toEqual({
      id: consultation.id,
      body: { name: 'Initial consultation', durationMinutes: 45, description: 'First visit' },
    })
    expect(await screen.findByText('45 min')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Initial consultation' })).toHaveFocus()
  })

  it('cancels an edit without saving', async () => {
    server.use(...sessionExists(admin), listOf(() => [consultation]))
    renderRoute('/admin/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit Initial consultation' }))
    await user.click(screen.getByRole('button', { name: /cancel/i }))

    expect(screen.queryByRole('form', { name: /edit/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit Initial consultation' })).toHaveFocus()
  })

  it('deactivates and reactivates a service', async () => {
    let services = [consultation]
    const sent: unknown[] = []
    server.use(
      ...sessionExists(admin),
      listOf(() => services),
      http.patch('*/api/admin/services/:id', async ({ request }) => {
        const body = (await request.json()) as { active: boolean }
        sent.push(body)
        services = [{ ...consultation, active: body.active }]
        return HttpResponse.json(services[0])
      }),
    )
    renderRoute('/admin/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Deactivate Initial consultation' }))
    await user.click(await screen.findByRole('button', { name: 'Reactivate Initial consultation' }))

    expect(await screen.findByRole('button', { name: 'Deactivate Initial consultation' })).toBeVisible()
    expect(sent).toEqual([{ active: false }, { active: true }])
  })

  it('is linked from the admin home page', async () => {
    server.use(...sessionExists(admin), listOf(() => []))
    const { router } = renderRoute('/admin')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Services' }))

    expect(router.state.location.pathname).toBe('/admin/services')
  })
})
