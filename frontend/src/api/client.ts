import createClient from 'openapi-fetch'
import type { paths } from './schema'

// Typed by the generated schema (`npm run generate:api`). Same origin: Vite in dev and
// Nginx in production proxy /api to the backend. Absolute, so it also works under Node in tests.
export const api = createClient<paths>({
  baseUrl: `${globalThis.location?.origin ?? ''}/api`,
  // Look fetch up per call instead of capturing it at import, so test mocks (MSW) can patch it.
  fetch: (request) => globalThis.fetch(request),
})
