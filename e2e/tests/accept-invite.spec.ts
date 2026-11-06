import { expect, test } from '@playwright/test'

// Accepting a provider invite (FR-1, #28). Emails aren't sent until M5, so the emailed link itself
// isn't followed here; the backend tests cover redeeming a real invite end to end.

test('an invalid invite link explains itself and points to the admin', async ({ page }) => {
  await page.goto('/accept-invite#token=not-a-real-token')
  await expect(page).toHaveURL(/\/accept-invite$/) // the token is cleared from the address bar

  await page.getByLabel('Password', { exact: true }).fill('a-brand-new-passphrase')
  await page.getByLabel('Confirm password').fill('a-brand-new-passphrase')
  await page.getByRole('button', { name: 'Set password' }).click()

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('This link is invalid or has expired.')
  await expect(alert).toContainText('Ask your admin for a new invite.')
})
