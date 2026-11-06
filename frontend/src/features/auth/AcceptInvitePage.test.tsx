import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ERROR_MESSAGES } from '../../lib/errors'
import { problem } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

async function choosePassword(password = 'a-brand-new-passphrase', confirm = password) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/^password/i), password)
  await user.type(screen.getByLabelText(/confirm/i), confirm)
  await user.click(screen.getByRole('button', { name: /set password/i }))
}

describe('AcceptInvitePage', () => {
  it('sets the first password with the token from the link, then goes to login', async () => {
    let sent: unknown
    server.use(
      http.post('*/api/auth/accept-invite', async ({ request }) => {
        sent = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router } = renderRoute('/accept-invite#token=invite123')
    // The token is read, then removed from the address bar.
    await waitFor(() => expect(router.state.location.hash).toBe(''))

    await choosePassword()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sent).toEqual({ token: 'invite123', password: 'a-brand-new-passphrase' })
    expect(await screen.findByRole('status')).toHaveTextContent(/log in with your email and new password/i)
  })

  it('shows INVALID_TOKEN and says to ask the admin for a new invite', async () => {
    server.use(http.post('*/api/auth/accept-invite', () => problem(400, { code: 'INVALID_TOKEN' })))
    renderRoute('/accept-invite#token=used-or-expired')

    await choosePassword()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(ERROR_MESSAGES.INVALID_TOKEN)
    expect(alert).toHaveTextContent(/ask your admin for a new invite/i)
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('treats a link without a token as invalid straight away', async () => {
    renderRoute('/accept-invite')

    expect(screen.getByRole('alert')).toHaveTextContent(/ask your admin for a new invite/i)
    expect(screen.queryByLabelText(/^password/i)).not.toBeInTheDocument()
  })

  it('shows a server password rule next to the field', async () => {
    server.use(
      http.post('*/api/auth/accept-invite', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'password', message: 'is too common; choose a less predictable password' }],
        }),
      ),
    )
    renderRoute('/accept-invite#token=invite123')

    await choosePassword('password1x', 'password1x')

    const field = screen.getByLabelText(/^password/i)
    await waitFor(() => expect(field).toHaveAttribute('aria-invalid', 'true'))
    expect(field).toHaveAccessibleDescription(/too common/i)
  })

  it('checks that both passwords match before sending', async () => {
    renderRoute('/accept-invite#token=invite123')

    await choosePassword('a-brand-new-passphrase', 'something-else-entirely')

    expect(screen.getByLabelText(/confirm/i)).toHaveAttribute('aria-invalid', 'true')
  })
})
