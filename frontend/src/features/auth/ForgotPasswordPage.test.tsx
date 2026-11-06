import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

describe('ForgotPasswordPage', () => {
  it('sends the email and shows the same confirmation whether or not the account exists', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/auth/forgot-password', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 202 })
      }),
    )
    renderRoute('/forgot-password')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/email/i), 'asha@example.com')
    await user.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(await screen.findByText(/if an account exists for asha@example.com/i)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/expires in 1 hour/i)
    expect(sent).toEqual({ email: 'asha@example.com' })
  })

  it('checks the email before sending anything', async () => {
    renderRoute('/forgot-password')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText(/email/i), 'not-an-email')
    await user.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-invalid', 'true')
  })

  it('is linked from the login page', async () => {
    const { router } = renderRoute('/login')
    const user = userEvent.setup()

    await user.click(screen.getByRole('link', { name: /forgot your password/i }))

    expect(router.state.location.pathname).toBe('/forgot-password')
  })
})
