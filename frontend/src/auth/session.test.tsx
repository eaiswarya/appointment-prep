import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { api } from '../api/client'
import { admin, customer, problem, sessionExists } from '../test/fixtures'
import { server } from '../test/msw'
import { renderRoute } from '../test/render'
import { expireSession, getAccessToken } from './tokenStore'

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

  it('finishes restoring even if /me fails after a successful refresh', async () => {
    server.use(
      http.post('*/api/auth/refresh', () => HttpResponse.json({ accessToken: 'restored' })),
      http.get('*/api/me', () => HttpResponse.error()),
    )

    renderRoute('/')

    expect(await screen.findByRole('link', { name: /log in/i })).toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
  })

  it('stays logged in and says so when the server logout fails', async () => {
    server.use(
      ...sessionExists(customer),
      http.post('*/api/auth/logout', () => new HttpResponse(null, { status: 500 })),
    )
    renderRoute('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /log out/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't log you out/i)
    expect(screen.getByText(customer.fullName)).toBeInTheDocument()
  })

  it('goes to /login when an API call gets 401 and the refresh fails too', async () => {
    server.use(...sessionExists(customer))
    const { router } = renderRoute('/')
    await screen.findByText(customer.fullName)
    server.use(
      http.get('*/api/me', () => problem(401, { code: 'TOKEN_EXPIRED' })),
      http.post('*/api/auth/refresh', () => problem(401, { code: 'UNAUTHENTICATED' })),
    )

    await api.GET('/me')

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
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
