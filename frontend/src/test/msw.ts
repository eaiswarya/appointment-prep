import { setupServer } from 'msw/node'

// Tests add handlers with server.use(...). Any request without a handler fails the test.
export const server = setupServer()
