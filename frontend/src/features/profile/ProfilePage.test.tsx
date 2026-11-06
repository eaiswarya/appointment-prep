import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { customer, problem, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

describe('ProfilePage', () => {
  it('shows the current name, time zone and (read-only) email', async () => {
    server.use(...sessionExists(customer))

    renderRoute('/profile')

    expect(await screen.findByLabelText(/full name/i)).toHaveValue(customer.fullName)
    expect(screen.getByLabelText(/time zone/i)).toHaveValue(customer.timezone)
    expect(screen.getByText(customer.email)).toBeInTheDocument()
  })

  it('saves, confirms, and updates the name in the header', async () => {
    let sent: unknown
    server.use(
      ...sessionExists(customer),
      http.patch('*/api/me', async ({ request }) => {
        sent = await request.json()
        const updated: components['schemas']['UserDto'] = {
          ...customer,
          fullName: 'Asha R. Rao',
          timezone: 'Europe/London',
        }
        return HttpResponse.json(updated)
      }),
    )
    renderRoute('/profile')
    const user = userEvent.setup()
    const name = await screen.findByLabelText(/full name/i)
    const zone = screen.getByLabelText(/time zone/i)

    await user.clear(name)
    await user.type(name, 'Asha R. Rao')
    await user.clear(zone)
    await user.type(zone, 'Europe/London')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sent).toEqual({ fullName: 'Asha R. Rao', timezone: 'Europe/London' })
    expect(screen.getByRole('banner')).toHaveTextContent('Asha R. Rao')
  })

  it('shows a server error next to the time zone field', async () => {
    server.use(
      ...sessionExists(customer),
      http.patch('*/api/me', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'timezone', message: 'must be a valid IANA time zone, e.g. Europe/London' }],
        }),
      ),
    )
    renderRoute('/profile')
    const user = userEvent.setup()
    const zone = await screen.findByLabelText(/time zone/i)

    await user.clear(zone)
    await user.type(zone, 'Mars/Olympus')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(zone).toHaveAttribute('aria-invalid', 'true'))
    expect(zone).toHaveAccessibleDescription(/valid IANA time zone/i)
    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('checks the name before sending anything', async () => {
    let patches = 0
    server.use(
      ...sessionExists(customer),
      http.patch('*/api/me', () => {
        patches++
        return HttpResponse.json(customer)
      }),
    )
    renderRoute('/profile')
    const user = userEvent.setup()
    const name = await screen.findByLabelText(/full name/i)

    await user.clear(name)
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(name).toHaveAttribute('aria-invalid', 'true')
    expect(patches).toBe(0)
  })

  it('hides the confirmation again once there are unsaved edits', async () => {
    server.use(
      ...sessionExists(customer),
      http.patch('*/api/me', () => HttpResponse.json({ ...customer, fullName: 'Asha R. Rao' })),
    )
    renderRoute('/profile')
    const user = userEvent.setup()
    const name = await screen.findByLabelText(/full name/i)
    await user.clear(name)
    await user.type(name, 'Asha R. Rao')
    await user.click(screen.getByRole('button', { name: /save/i }))
    expect(await screen.findByText(/your profile is saved/i)).toBeInTheDocument()

    await user.type(name, '!')

    expect(screen.getByRole('status')).toBeEmptyDOMElement()
  })

  it('tells providers their appointments use the provider time zone', async () => {
    server.use(...sessionExists({ ...customer, role: 'PROVIDER' }))

    renderRoute('/profile')

    expect(await screen.findByLabelText(/time zone/i)).toHaveAccessibleDescription(/provider settings/i)
  })

  it('sends a visitor who is not logged in to the login page', async () => {
    const { router } = renderRoute('/profile')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })

  it('is linked from the name in the header', async () => {
    server.use(...sessionExists(customer))
    const { router } = renderRoute('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: customer.fullName }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/profile'))
  })
})
