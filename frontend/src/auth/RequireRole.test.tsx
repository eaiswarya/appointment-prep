import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { loginSucceeds } from '../test/fixtures'
import { server } from '../test/msw'
import { renderRoute } from '../test/render'

describe('RequireRole', () => {
  it('sends a visitor who is not logged in to the login page', async () => {
    const { router } = renderRoute('/admin')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(screen.queryByRole('heading', { name: /admin/i })).not.toBeInTheDocument()
  })

  it('redirects a CUSTOMER away from an ADMIN route (UX only; the API enforces access)', async () => {
    server.use(loginSucceeds())
    const { router } = renderRoute('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/email/i), 'asha@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: /log in/i }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))

    await router.navigate('/admin')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(screen.queryByRole('heading', { name: /admin/i })).not.toBeInTheDocument()
  })

  it('lets an ADMIN in', async () => {
    server.use(
      loginSucceeds({
        id: '0190b7a0-0000-7000-8000-000000000009',
        email: 'admin@example.com',
        fullName: 'Ada Admin',
        role: 'ADMIN',
        timezone: 'UTC',
      }),
    )
    const { router } = renderRoute('/login')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/email/i), 'admin@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'correct-horse-battery')
    await user.click(screen.getByRole('button', { name: /log in/i }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))

    await router.navigate('/admin')

    expect(await screen.findByRole('heading', { name: /admin/i })).toBeInTheDocument()
  })
})
