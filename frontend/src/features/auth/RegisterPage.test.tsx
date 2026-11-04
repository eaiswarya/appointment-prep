import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ERROR_MESSAGES, FALLBACK_MESSAGE } from '../../lib/errors'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

const REGISTER = '*/api/auth/register'

function problem(status: number, body: Record<string, unknown>) {
  return HttpResponse.json(
    { type: 'about:blank', status, ...body },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

async function fillAndSubmit() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText(/full name/i), 'Asha Rao')
  await user.type(screen.getByLabelText(/email/i), 'asha@example.com')
  await user.type(screen.getByLabelText(/^password/i), 'correct-horse-battery')
  await user.click(screen.getByRole('button', { name: /create account/i }))
}

describe('RegisterPage', () => {
  it('sends the details with the browser time zone, then goes to login with a notice', async () => {
    let sent: Record<string, unknown> | undefined
    server.use(
      http.post(REGISTER, async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(
          { id: 'u1', email: 'asha@example.com', fullName: 'Asha Rao', role: 'CUSTOMER', timezone: 'UTC' },
          { status: 201 },
        )
      }),
    )
    const { router } = renderRoute('/register')

    await fillAndSubmit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(sent).toEqual({
      fullName: 'Asha Rao',
      email: 'asha@example.com',
      password: 'correct-horse-battery',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    })
    expect(await screen.findByRole('status')).toHaveTextContent(/you can log in now/i)
  })

  it('shows each server field error next to its input', async () => {
    server.use(
      http.post(REGISTER, () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'password', message: 'is too common; choose a less predictable password' }],
        }),
      ),
    )
    renderRoute('/register')

    await fillAndSubmit()

    const password = screen.getByLabelText(/^password/i)
    await waitFor(() => expect(password).toHaveAttribute('aria-invalid', 'true'))
    expect(password).toHaveAccessibleDescription(/too common/i)
  })

  it('shows the errors.ts message on the email field when the email is taken', async () => {
    server.use(http.post(REGISTER, () => problem(409, { code: 'EMAIL_TAKEN' })))
    renderRoute('/register')

    await fillAndSubmit()

    const email = screen.getByLabelText(/email/i)
    await waitFor(() => expect(email).toHaveAttribute('aria-invalid', 'true'))
    expect(email).toHaveAccessibleDescription(ERROR_MESSAGES.EMAIL_TAKEN)
  })

  it('shows the fallback message for an unexpected server error', async () => {
    server.use(http.post(REGISTER, () => problem(500, { title: 'Internal server error' })))
    renderRoute('/register')

    await fillAndSubmit()

    expect(await screen.findByRole('alert')).toHaveTextContent(FALLBACK_MESSAGE)
  })

  it('checks required fields before sending anything', async () => {
    // No handler: any request would fail the test (onUnhandledFrame: 'error').
    renderRoute('/register')
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(screen.getByLabelText(/full name/i)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/email/i)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText(/^password/i)).toHaveAccessibleDescription(/at least 8 characters/i)
  })
})
