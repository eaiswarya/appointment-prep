import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { setAccessToken } from '../auth/tokenStore'
import { customer } from '../test/fixtures'
import { server } from '../test/msw'
import { api } from './client'

describe('api client', () => {
  afterEach(() => {
    setAccessToken(null)
  })

  it('sends the in-memory access token as a Bearer header', async () => {
    let authorization: string | null = null
    server.use(
      http.get('*/api/me', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json(customer)
      }),
    )
    setAccessToken('abc.def.ghi')

    await api.GET('/me')

    expect(authorization).toBe('Bearer abc.def.ghi')
  })

  it('sends no Authorization header when logged out', async () => {
    let authorization: string | null = 'unset'
    server.use(
      http.get('*/api/me', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json(customer)
      }),
    )

    await api.GET('/me')

    expect(authorization).toBeNull()
  })

  it('never sends the token to /auth/* calls', async () => {
    let authorization: string | null = 'unset'
    server.use(
      http.post('*/api/auth/login', ({ request }) => {
        authorization = request.headers.get('Authorization')
        return HttpResponse.json({ accessToken: 't', user: customer })
      }),
    )
    setAccessToken('stale.expired.token')

    await api.POST('/auth/login', { body: { email: 'a@example.com', password: 'x' } })

    expect(authorization).toBeNull()
  })
})
