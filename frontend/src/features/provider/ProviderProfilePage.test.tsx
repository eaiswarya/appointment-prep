import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { problem, provider, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

type ProviderProfileDto = components['schemas']['ProviderProfileDto']

const profile: ProviderProfileDto = {
  displayName: 'Dr Iyer',
  bio: 'Family medicine.',
  timezone: 'Asia/Kolkata',
  bufferMinutes: 10,
}

function profileIs(current: () => ProviderProfileDto) {
  return http.get('*/api/provider/me', () => HttpResponse.json(current()))
}

describe('ProviderProfilePage', () => {
  it('shows the current profile', async () => {
    server.use(...sessionExists(provider), profileIs(() => profile))

    renderRoute('/provider/profile')

    expect(await screen.findByLabelText(/display name/i)).toHaveValue('Dr Iyer')
    expect(screen.getByLabelText(/bio/i)).toHaveValue('Family medicine.')
    expect(screen.getByLabelText(/time zone/i)).toHaveValue('Asia/Kolkata')
    expect(screen.getByLabelText(/buffer/i)).toHaveValue(10)
  })

  it('saves the profile and confirms', async () => {
    let current = profile
    let sent: unknown
    server.use(
      ...sessionExists(provider),
      profileIs(() => current),
      http.patch('*/api/provider/me', async ({ request }) => {
        sent = await request.json()
        current = { ...profile, displayName: 'Dr Meera Iyer', bufferMinutes: 15 }
        return HttpResponse.json(current)
      }),
    )
    renderRoute('/provider/profile')
    const user = userEvent.setup()

    const name = await screen.findByLabelText(/display name/i)
    await user.clear(name)
    await user.type(name, 'Dr Meera Iyer')
    const buffer = screen.getByLabelText(/buffer/i)
    await user.clear(buffer)
    await user.type(buffer, '15')
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sent).toEqual({
      displayName: 'Dr Meera Iyer',
      bio: 'Family medicine.',
      timezone: 'Asia/Kolkata',
      bufferMinutes: 15,
    })
  })

  it('explains what a time-zone change does before it is saved', async () => {
    server.use(...sessionExists(provider), profileIs(() => profile))
    renderRoute('/provider/profile')
    const user = userEvent.setup()

    const zone = await screen.findByLabelText(/time zone/i)
    expect(screen.queryByText(/same local times/i)).not.toBeInTheDocument()

    await user.clear(zone)
    await user.type(zone, 'Europe/London')

    expect(screen.getByText(/same local times/i)).toHaveTextContent('Europe/London')
  })

  it('says the buffer only affects new bookings', async () => {
    server.use(...sessionExists(provider), profileIs(() => profile))

    renderRoute('/provider/profile')

    expect(await screen.findByLabelText(/buffer/i)).toHaveAccessibleDescription(/new bookings/i)
  })

  it('shows a server field error next to the buffer', async () => {
    server.use(
      ...sessionExists(provider),
      profileIs(() => profile),
      http.patch('*/api/provider/me', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'bufferMinutes', message: 'must be a multiple of 5' }],
        }),
      ),
    )
    renderRoute('/provider/profile')
    const user = userEvent.setup()

    const buffer = await screen.findByLabelText(/buffer/i)
    await user.clear(buffer)
    await user.type(buffer, '7')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() => expect(buffer).toHaveAttribute('aria-invalid', 'true'))
    expect(buffer).toHaveAccessibleDescription(/multiple of 5/i)
  })

  it('shows an error if the profile cannot be loaded', async () => {
    server.use(
      ...sessionExists(provider),
      http.get('*/api/provider/me', () => problem(500, { title: 'Internal server error' })),
    )

    renderRoute('/provider/profile')

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
  })
})
