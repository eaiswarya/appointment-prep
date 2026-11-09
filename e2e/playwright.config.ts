import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests against the running Docker Compose stack (TECHNICAL_DESIGN §11, §14).
 * Start it first: `docker compose up -d --build --wait`. Override the URL with E2E_BASE_URL.
 * The stack's per-IP rate limits apply (TECHNICAL_DESIGN §8.3): forgot-password allows 3 per hour.
 * Between local reruns, `docker compose restart backend` clears them.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
