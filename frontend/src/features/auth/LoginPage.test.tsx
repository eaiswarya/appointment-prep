import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ERROR_MESSAGES } from '../../lib/errors'
import { customer, loginSucceeds, problem } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

async function logIn(email = 'asha@example.com', password = 'correct-horse-battery') {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/email/i), email)
  await user.type(screen.getByLabelText(/^password/i), password)
  await user.click(screen.getByRole('button', { name: /log in/i }))
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('logs in, shows the user name, and keeps the token out of browser storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    server.use(loginSucceeds())
    const { router } = renderRoute('/login')

    await logIn()

    expect(await screen.findByText(customer.fullName)).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(setItem).not.toHaveBeenCalled()
  })

  it('returns to the page the user came from', async () => {
    server.use(loginSucceeds())
    const { router } = renderRoute('/login', { from: '/profile' })

    await logIn()

    await waitFor(() => expect(router.state.location.pathname).toBe('/profile'))
  })

  it('says the email or password is wrong without saying which', async () => {
    server.use(http.post('*/api/auth/login', () => problem(401, { code: 'UNAUTHENTICATED' })))
    renderRoute('/login')

    await logIn('asha@example.com', 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent(/email or password is incorrect/i)
    expect(screen.queryByText(customer.fullName)).not.toBeInTheDocument()
  })

  it('says to wait when there have been too many attempts', async () => {
    server.use(
      http.post('*/api/auth/login', () =>
        problem(429, { code: 'RATE_LIMITED', detail: 'Too many attempts. Try again in 40 seconds.' }),
      ),
    )
    renderRoute('/login')

    await logIn('asha@example.com', 'another-guess')

    expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_MESSAGES.RATE_LIMITED)
  })

  it('checks both fields before sending anything', async () => {
    renderRoute('/login')
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/^password/i)).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the notice after registering', async () => {
    renderRoute('/login', { registered: true, email: 'asha@example.com' })

    expect(screen.getByRole('status')).toHaveTextContent(/you can log in now as asha@example.com/i)
    expect(screen.getByRole('heading', { name: /log in/i })).toHaveFocus()
  })
})
