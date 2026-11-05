import { expect, test } from '@playwright/test'

// Register → log in → see your name → reload and stay logged in → log out (C1; #20–#22).
test('a customer can register, log in, stay logged in across a reload, and log out', async ({ page }) => {
  // Unique per run, so reruns against the same database don't collide.
  const email = `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
  const password = 'correct-horse-battery'
  const name = 'E2E Tester'
  const header = page.getByRole('banner')

  await test.step('register', async () => {
    await page.goto('/register')
    await page.getByLabel('Full name').fill(name)
    await page.getByLabel('Email').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByRole('button', { name: 'Create account' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByRole('status')).toContainText('You can log in now')
  })

  await test.step('log in (email is prefilled)', async () => {
    await expect(page.getByLabel('Email')).toHaveValue(email)
    await page.getByLabel('Password', { exact: true }).fill(password)
    await page.getByRole('button', { name: 'Log in' }).click()

    await expect(page).toHaveURL(/\/$/)
    await expect(header).toContainText(name)
  })

  await test.step('stay logged in across a reload (refresh cookie)', async () => {
    await page.reload()

    await expect(header).toContainText(name)
  })

  await test.step('log out, and stay logged out after a reload', async () => {
    await header.getByRole('button', { name: 'Log out' }).click()

    await expect(header.getByRole('link', { name: 'Log in' })).toBeVisible()
    await expect(header).not.toContainText(name)

    await page.reload()
    await expect(header.getByRole('link', { name: 'Log in' })).toBeVisible()
  })
})

test('a wrong password shows one generic message', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Email').fill('nobody@example.com')
  await page.getByLabel('Password', { exact: true }).fill('wrong-password-123')
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page.getByRole('alert')).toHaveText('The email or password is incorrect.')
})
