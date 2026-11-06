import { delay, http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, onSessionExpired, setAccessToken } from '../auth/tokenStore'
import { customer, problem } from '../test/fixtures'
import { server } from '../test/msw'
import { api, refreshAccessToken } from './client'

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

  it('on a 401, refreshes once and retries the call with the new token', async () => {
    const seen: (string | null)[] = []
    let refreshes = 0
    server.use(
      http.get('*/api/me', ({ request }) => {
        const authorization = request.headers.get('Authorization')
        seen.push(authorization)
        return authorization === 'Bearer fresh'
          ? HttpResponse.json(customer)
          : problem(401, { code: 'TOKEN_EXPIRED' })
      }),
      http.post('*/api/auth/refresh', () => {
        refreshes++
        return HttpResponse.json({ accessToken: 'fresh' })
      }),
    )
    setAccessToken('expired')

    const { data, response } = await api.GET('/me')

    expect(response.status).toBe(200)
    expect(data?.email).toBe(customer.email)
    expect(refreshes).toBe(1)
    expect(seen).toEqual(['Bearer expired', 'Bearer fresh'])
    expect(getAccessToken()).toBe('fresh')
  })

  it('retries a request with a body (PATCH) with the same body after refreshing', async () => {
    const bodies: unknown[] = []
    server.use(
      http.patch('*/api/me', async ({ request }) => {
        bodies.push(await request.json())
        return request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json({ ...customer, fullName: 'New Name' })
          : problem(401, { code: 'TOKEN_EXPIRED' })
      }),
      http.post('*/api/auth/refresh', () => HttpResponse.json({ accessToken: 'fresh' })),
    )
    setAccessToken('expired')

    const { data, response } = await api.PATCH('/me', { body: { fullName: 'New Name' } })

    expect(response.status).toBe(200)
    expect(data?.fullName).toBe('New Name')
    expect(bodies).toEqual([{ fullName: 'New Name' }, { fullName: 'New Name' }])
  })

  it('shares one in-flight refresh between concurrent 401s', async () => {
    let refreshes = 0
    server.use(
      http.get('*/api/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh'
          ? HttpResponse.json(customer)
          : problem(401, { code: 'TOKEN_EXPIRED' }),
      ),
      http.post('*/api/auth/refresh', async () => {
        refreshes++
        await delay(50)
        return HttpResponse.json({ accessToken: 'fresh' })
      }),
    )
    setAccessToken('expired')

    const results = await Promise.all([api.GET('/me'), api.GET('/me'), api.GET('/me')])

    expect(results.map((r) => r.response.status)).toEqual([200, 200, 200])
    expect(refreshes).toBe(1)
  })

  it('keeps the session when the refresh fails for another reason (e.g. a 503)', async () => {
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    server.use(
      http.get('*/api/me', () => problem(401, { code: 'TOKEN_EXPIRED' })),
      http.post('*/api/auth/refresh', () => new HttpResponse(null, { status: 503 })),
    )
    setAccessToken('expired')

    const { response } = await api.GET('/me')

    expect(response.status).toBe(401)
    expect(expired).not.toHaveBeenCalled()
    unsubscribe()
  })

  it('serialises refreshes across tabs with navigator.locks when available', async () => {
    const request = vi.fn((_name: string, callback: () => Promise<unknown>) => callback())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })
    server.use(http.post('*/api/auth/refresh', () => HttpResponse.json({ accessToken: 'fresh' })))

    await expect(refreshAccessToken()).resolves.toBe('fresh')

    expect(request).toHaveBeenCalledWith('auth-refresh', expect.any(Function))
    vi.unstubAllGlobals()
  })

  it('does not wipe a newer login when a slow refresh fails', async () => {
    server.use(
      http.post('*/api/auth/refresh', async () => {
        await delay(50)
        return problem(401, { code: 'UNAUTHENTICATED' })
      }),
    )

    const pending = refreshAccessToken()
    setAccessToken('just-logged-in')
    await pending

    expect(getAccessToken()).toBe('just-logged-in')
  })

  it('ends the session when the refresh also fails', async () => {
    const expired = vi.fn()
    const unsubscribe = onSessionExpired(expired)
    server.use(http.get('*/api/me', () => problem(401, { code: 'TOKEN_EXPIRED' })))
    setAccessToken('expired')

    const { response } = await api.GET('/me')

    expect(response.status).toBe(401)
    expect(expired).toHaveBeenCalledOnce()
    expect(getAccessToken()).toBeNull()
    unsubscribe()
  })
})
