import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ERROR_MESSAGES } from '../../lib/errors'
import { problem } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

async function setPassword(password = 'a-brand-new-passphrase', confirm = password) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/^new password/i), password)
  await user.type(screen.getByLabelText(/confirm/i), confirm)
  await user.click(screen.getByRole('button', { name: /set new password/i }))
}

describe('ResetPasswordPage', () => {
  it('sets the new password with the token from the link, then goes to login', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/auth/reset-password', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router } = renderRoute('/reset-password#token=abc123')
    // The token is read, then removed from the address bar.
    await waitFor(() => expect(router.state.location.hash).toBe(''))

    await setPassword()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sent).toEqual({ token: 'abc123', newPassword: 'a-brand-new-passphrase' })
    expect(await screen.findByRole('status')).toHaveTextContent(/password has been changed/i)
  })

  it('shows INVALID_TOKEN and offers a new link', async () => {
    server.use(http.post('*/api/auth/reset-password', () => problem(400, { code: 'INVALID_TOKEN' })))
    const { router } = renderRoute('/reset-password#token=used-or-expired')
    const user = userEvent.setup()

    await setPassword()

    expect(await screen.findByRole('alert')).toHaveTextContent(ERROR_MESSAGES.INVALID_TOKEN)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveFocus())
    await user.click(screen.getByRole('link', { name: /request a new link/i }))
    expect(router.state.location.pathname).toBe('/forgot-password')
  })

  it('treats a link without a token as invalid straight away', async () => {
    renderRoute('/reset-password')

    expect(screen.getByRole('alert')).toHaveTextContent(ERROR_MESSAGES.INVALID_TOKEN)
    expect(screen.getByRole('link', { name: /request a new link/i })).toBeInTheDocument()
    expect(screen.queryByLabelText(/^new password/i)).not.toBeInTheDocument()
  })

  it('shows a server password rule next to the field', async () => {
    server.use(
      http.post('*/api/auth/reset-password', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'newPassword', message: 'is too common; choose a less predictable password' }],
        }),
      ),
    )
    renderRoute('/reset-password#token=abc123')

    await setPassword('password1x', 'password1x')

    const field = screen.getByLabelText(/^new password/i)
    await waitFor(() => expect(field).toHaveAttribute('aria-invalid', 'true'))
    expect(field).toHaveAccessibleDescription(/too common/i)
  })

  it('checks that both passwords match before sending', async () => {
    renderRoute('/reset-password#token=abc123')

    await setPassword('a-brand-new-passphrase', 'something-else-entirely')

    expect(screen.getByLabelText(/confirm/i)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/confirm/i)).toHaveAccessibleDescription(/match/i)
  })
})
