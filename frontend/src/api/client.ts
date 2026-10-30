import createClient from 'openapi-fetch'
import type { paths } from './schema'

// Typed by the generated schema (`npm run generate:api`). Same origin: Vite in dev and
// Nginx in production proxy /api to the backend.
export const api = createClient<paths>({ baseUrl: '/api' })
