import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { setAccessToken } from '../auth/tokenStore'
import { server } from './msw'

// Pages that restore a session and then load data make several mocked calls before rendering;
// under full-suite load that can exceed the 1 s default for findBy*/waitFor and flake.
configure({ asyncUtilTimeout: 3000 })

beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' })
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  setAccessToken(null)
})

afterAll(() => {
  server.close()
})
