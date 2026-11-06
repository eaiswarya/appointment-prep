import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { ERROR_MESSAGES } from '../../lib/errors'
import { admin, customer, problem, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

type ProviderDto = components['schemas']['ProviderDto']
type Page = components['schemas']['PageResponseProviderDto']

const pending: ProviderDto = {
  id: '0190b7a0-0000-7000-8000-0000000000a1',
  email: 'iyer@example.com',
  fullName: 'Dr Meera Iyer',
  displayName: 'Dr Iyer',
  timezone: 'Asia/Kolkata',
  active: true,
  invitePending: true,
}
const accepted: ProviderDto = {
  ...pending,
  id: '0190b7a0-0000-7000-8000-0000000000a2',
  email: 'shah@example.com',
  fullName: 'Dr Ravi Shah',
  displayName: 'Dr Shah',
  invitePending: false,
}

function listOf(items: ProviderDto[]) {
  const page: Page = { items, page: 0, size: 20, total: items.length }
  return http.get('*/api/admin/providers', () => HttpResponse.json(page))
}

describe('AdminProvidersPage', () => {
  it('lists providers and marks pending invites', async () => {
    server.use(...sessionExists(admin), listOf([pending, accepted]))

    renderRoute('/admin/providers')

    const list = await screen.findByRole('list', { name: /providers/i })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('Dr Iyer')
    expect(items[0]).toHaveTextContent('Invite pending')
    expect(items[1]).toHaveTextContent('Dr Shah')
    expect(items[1]).toHaveTextContent('Active')
  })

  it('says so when there are no providers yet', async () => {
    server.use(...sessionExists(admin), listOf([]))

    renderRoute('/admin/providers')

    expect(await screen.findByText(/no providers yet/i)).toBeInTheDocument()
  })

  it('invites a provider, confirms, and refreshes the list', async () => {
    let sent: unknown
    let invited = false
    server.use(
      ...sessionExists(admin),
      http.get('*/api/admin/providers', () =>
        HttpResponse.json({ items: invited ? [pending] : [], page: 0, size: 20, total: invited ? 1 : 0 }),
      ),
      http.post('*/api/admin/providers', async ({ request }) => {
        sent = await request.json()
        invited = true
        return HttpResponse.json(pending, { status: 201 })
      }),
    )
    renderRoute('/admin/providers')
    const user = userEvent.setup()
    await screen.findByText(/no providers yet/i)

    await user.type(screen.getByLabelText(/full name/i), 'Dr Meera Iyer')
    await user.type(screen.getByLabelText(/display name/i), 'Dr Iyer')
    await user.type(screen.getByLabelText(/email/i), 'iyer@example.com')
    const zone = screen.getByLabelText(/time zone/i)
    await user.clear(zone)
    await user.type(zone, 'Asia/Kolkata')
    await user.click(screen.getByRole('button', { name: /send invite/i }))

    expect(await screen.findByText(/invitation sent to iyer@example.com/i)).toBeInTheDocument()
    expect(sent).toEqual({
      fullName: 'Dr Meera Iyer',
      displayName: 'Dr Iyer',
      email: 'iyer@example.com',
      timezone: 'Asia/Kolkata',
    })
    const list = await screen.findByRole('list', { name: /providers/i })
    expect(within(list).getByText('Dr Iyer')).toBeInTheDocument()
    expect(screen.getByLabelText(/email/i)).toHaveValue('')
  })

  it('shows EMAIL_TAKEN on the email field', async () => {
    server.use(
      ...sessionExists(admin),
      listOf([]),
      http.post('*/api/admin/providers', () => problem(409, { code: 'EMAIL_TAKEN' })),
    )
    renderRoute('/admin/providers')
    const user = userEvent.setup()
    await screen.findByText(/no providers yet/i)

    await user.type(screen.getByLabelText(/full name/i), 'Someone')
    await user.type(screen.getByLabelText(/display name/i), 'Someone')
    await user.type(screen.getByLabelText(/email/i), 'taken@example.com')
    await user.click(screen.getByRole('button', { name: /send invite/i }))

    const email = screen.getByLabelText(/email/i)
    await waitFor(() => expect(email).toHaveAttribute('aria-invalid', 'true'))
    expect(email).toHaveAccessibleDescription(ERROR_MESSAGES.EMAIL_TAKEN)
  })

  it('keeps customers out (UX only; the API returns 403)', async () => {
    server.use(...sessionExists(customer))

    const { router } = renderRoute('/admin/providers')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })

  it('is linked from the admin home', async () => {
    server.use(...sessionExists(admin), listOf([]))
    const { router } = renderRoute('/admin')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: /providers/i }))

    expect(router.state.location.pathname).toBe('/admin/providers')
  })
})
