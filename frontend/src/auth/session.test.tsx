import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { admin, customer, sessionExists } from '../test/fixtures'
import { server } from '../test/msw'
import { renderRoute } from '../test/render'
import { expireSession } from './tokenStore'

describe('session', () => {
  it('restores the session on load (e.g. after a page reload)', async () => {
    server.use(...sessionExists(customer))

    renderRoute('/')

    expect(await screen.findByText(customer.fullName)).toBeInTheDocument()
  })

  it('stays logged out when there is no session to restore', async () => {
    renderRoute('/')

    expect(await screen.findByRole('link', { name: /log in/i })).toBeInTheDocument()
    expect(screen.queryByText(customer.fullName)).not.toBeInTheDocument()
  })

  it('waits for the restore before guarding a route', async () => {
    server.use(...sessionExists(admin))

    const { router } = renderRoute('/admin')

    expect(await screen.findByRole('heading', { name: /admin/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin')
  })

  it('logs out: revokes the session and shows the logged-out header', async () => {
    let loggedOut = false
    server.use(
      ...sessionExists(customer),
      http.post('*/api/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderRoute('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /log out/i }))

    expect(await screen.findByRole('link', { name: /log in/i })).toBeInTheDocument()
    expect(loggedOut).toBe(true)
    expect(screen.queryByText(customer.fullName)).not.toBeInTheDocument()
  })

  it('goes to /login when the session ends (refresh failed)', async () => {
    server.use(...sessionExists(customer))
    const { router } = renderRoute('/')
    await screen.findByText(customer.fullName)

    expireSession()

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(screen.queryByText(customer.fullName)).not.toBeInTheDocument()
  })
})
