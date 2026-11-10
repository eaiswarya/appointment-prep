import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { customer, provider, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

describe('Provider area', () => {
  it('is linked from the header for providers, and lists its pages', async () => {
    server.use(...sessionExists(provider))
    const { router } = renderRoute('/profile')
    const user = userEvent.setup()

    const header = await screen.findByRole('banner')
    await user.click(await within(header).findByRole('link', { name: 'Provider area' }))

    expect(router.state.location.pathname).toBe('/provider')
    await user.click(await screen.findByRole('link', { name: 'Profile' }))
    expect(router.state.location.pathname).toBe('/provider/profile')
  })

  it('is not linked for customers', async () => {
    server.use(...sessionExists(customer))

    renderRoute('/profile')

    const header = await screen.findByRole('banner')
    await within(header).findByRole('link', { name: customer.fullName })
    expect(within(header).queryByRole('link', { name: 'Provider area' })).not.toBeInTheDocument()
  })

  it.each(['/provider', '/provider/profile'])('sends a customer away from %s', async (path) => {
    server.use(...sessionExists(customer))

    const { router } = renderRoute(path)

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
  })
})
