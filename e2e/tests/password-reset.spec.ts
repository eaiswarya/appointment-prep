import { expect, test } from '@playwright/test'

// Forgot / reset password (FR-3, #26). Emails aren't sent until M5, so the emailed link itself
// isn't followed here; the backend tests cover token redemption end to end.

test('forgot password shows one confirmation, whether or not the account exists', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot your password?' }).click()
  await expect(page).toHaveURL(/\/forgot-password$/)

  const email = `nobody-${Date.now()}@example.com`
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Send reset link' }).click()

  await expect(page.getByRole('status')).toContainText(`If an account exists for ${email}`)
})

test('an invalid reset link explains itself and offers a new one', async ({ page }) => {
  await page.goto('/reset-password?token=not-a-real-token')
  await page.getByLabel('New password', { exact: true }).fill('a-brand-new-passphrase')
  await page.getByLabel('Confirm new password').fill('a-brand-new-passphrase')
  await page.getByRole('button', { name: 'Set new password' }).click()

  await expect(page.getByRole('alert')).toContainText('This link is invalid or has expired.')
  await page.getByRole('link', { name: 'Request a new link' }).click()
  await expect(page).toHaveURL(/\/forgot-password$/)
})
