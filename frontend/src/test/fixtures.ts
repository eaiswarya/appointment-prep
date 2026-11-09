import { http, HttpResponse } from 'msw'
import type { components } from '../api/schema'

type UserDto = components['schemas']['UserDto']
type LoginResponse = components['schemas']['LoginResponse']
type RefreshResponse = components['schemas']['RefreshResponse']

export const customer: UserDto = {
  id: '0190b7a0-0000-7000-8000-000000000001',
  email: 'asha@example.com',
  fullName: 'Asha Rao',
  role: 'CUSTOMER',
  timezone: 'Asia/Kolkata',
}

export const admin: UserDto = {
  id: '0190b7a0-0000-7000-8000-000000000009',
  email: 'admin@example.com',
  fullName: 'Ada Admin',
  role: 'ADMIN',
  timezone: 'UTC',
}

export const provider: UserDto = {
  id: '0190b7a0-0000-7000-8000-000000000005',
  email: 'iyer@example.com',
  fullName: 'Dr Meera Iyer',
  role: 'PROVIDER',
  timezone: 'Asia/Kolkata',
}

export function problem(status: number, body: Record<string, unknown>) {
  return HttpResponse.json(
    { type: 'about:blank', status, ...body },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

/** POST /api/auth/login that succeeds for `user` with `accessToken`. */
export function loginSucceeds(user: UserDto = customer, accessToken = 'test-access-token') {
  const body: LoginResponse = { accessToken, user }
  return http.post('*/api/auth/login', () => HttpResponse.json(body))
}

/** A restorable session: refresh succeeds and /me returns `user` (as after a page reload). */
export function sessionExists(user: UserDto = customer, accessToken = 'restored-access-token') {
  const body: RefreshResponse = { accessToken }
  return [
    http.post('*/api/auth/refresh', () => HttpResponse.json(body)),
    http.get('*/api/me', () => HttpResponse.json(user)),
  ]
}
