import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { components } from '../../api/schema'
import { ERROR_MESSAGES } from '../../lib/errors'
import { problem, provider, sessionExists } from '../../test/fixtures'
import { server } from '../../test/msw'
import { renderRoute } from '../../test/render'

type ServiceDto = components['schemas']['ServiceDto']
type ProviderServicesDto = components['schemas']['ProviderServicesDto']

const consultation: ServiceDto = {
  id: '0190b7a0-0000-7000-8000-0000000000c1',
  name: 'Initial consultation',
  description: 'First visit',
  durationMinutes: 30,
  active: true,
}
const followUp: ServiceDto = {
  id: '0190b7a0-0000-7000-8000-0000000000c2',
  name: 'Follow-up',
  durationMinutes: 15,
  active: true,
}

function servicesAre(current: () => ProviderServicesDto) {
  return http.get('*/api/provider/services', () => HttpResponse.json(current()))
}

describe('ProviderServicesPage', () => {
  it('lists active services with the ones offered ticked', async () => {
    server.use(
      ...sessionExists(provider),
      servicesAre(() => ({ offeredServiceIds: [consultation.id], available: [consultation, followUp] })),
    )

    renderRoute('/provider/services')

    expect(await screen.findByRole('checkbox', { name: /initial consultation/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /initial consultation/i })).toHaveAccessibleName(/30 min/)
    expect(screen.getByRole('checkbox', { name: /follow-up/i })).not.toBeChecked()
    expect(screen.getByRole('group', { name: /services you offer/i })).toBeInTheDocument()
  })

  it('saves the new set and confirms', async () => {
    let current: ProviderServicesDto = { offeredServiceIds: [consultation.id], available: [consultation, followUp] }
    let sent: unknown
    server.use(
      ...sessionExists(provider),
      servicesAre(() => current),
      http.put('*/api/provider/services', async ({ request }) => {
        sent = await request.json()
        current = { ...current, offeredServiceIds: [followUp.id] }
        return HttpResponse.json(current)
      }),
    )
    renderRoute('/provider/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: /initial consultation/i }))
    await user.click(screen.getByRole('checkbox', { name: /follow-up/i }))
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sent).toEqual({ serviceIds: [followUp.id] })
    expect(screen.getByRole('checkbox', { name: /follow-up/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /initial consultation/i })).not.toBeChecked()
  })

  it('can offer nothing', async () => {
    let sent: unknown
    server.use(
      ...sessionExists(provider),
      servicesAre(() => ({ offeredServiceIds: [consultation.id], available: [consultation] })),
      http.put('*/api/provider/services', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ offeredServiceIds: [], available: [consultation] })
      }),
    )
    renderRoute('/provider/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: /initial consultation/i }))
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sent).toEqual({ serviceIds: [] })
  })

  it('explains when the catalog is empty', async () => {
    server.use(...sessionExists(provider), servicesAre(() => ({ offeredServiceIds: [], available: [] })))

    renderRoute('/provider/services')

    expect(await screen.findByText(/no services in the catalog yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save/i })).not.toBeInTheDocument()
  })

  it('reloads the list when a chosen service is no longer available', async () => {
    let gets = 0
    server.use(
      ...sessionExists(provider),
      http.get('*/api/provider/services', () => {
        gets += 1
        return HttpResponse.json({ offeredServiceIds: [], available: gets === 1 ? [consultation, followUp] : [followUp] })
      }),
      http.put('*/api/provider/services', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'serviceIds', message: 'must only contain services that exist and are active' }],
        }),
      ),
    )
    renderRoute('/provider/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: /initial consultation/i }))
    await user.click(screen.getByRole('button', { name: /save/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available/i)
    await waitFor(() =>
      expect(screen.queryByRole('checkbox', { name: /initial consultation/i })).not.toBeInTheDocument(),
    )
  })

  it('shows a reactivated service as offered again after a refresh, so saving keeps it', async () => {
    // The provider offers both; consultation was deactivated, then reactivated while the page is open.
    let gets = 0
    let sent: unknown
    server.use(
      ...sessionExists(provider),
      http.get('*/api/provider/services', () => {
        gets += 1
        return HttpResponse.json(
          gets === 1
            ? { offeredServiceIds: [followUp.id], available: [followUp] }
            : { offeredServiceIds: [consultation.id, followUp.id], available: [consultation, followUp] },
        )
      }),
      http.put('*/api/provider/services', async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ offeredServiceIds: [consultation.id], available: [consultation, followUp] })
      }),
    )
    renderRoute('/provider/services')
    const user = userEvent.setup()
    await screen.findByRole('checkbox', { name: /follow-up/i })

    window.dispatchEvent(new Event('visibilitychange'))

    expect(await screen.findByRole('checkbox', { name: /initial consultation/i })).toBeChecked()
    await user.click(screen.getByRole('checkbox', { name: /follow-up/i }))
    await user.click(screen.getByRole('button', { name: /save/i }))
    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i)
    expect(sent).toEqual({ serviceIds: [consultation.id] })
  })

  it('shows other validation problems as they are, not as "no longer available"', async () => {
    server.use(
      ...sessionExists(provider),
      servicesAre(() => ({ offeredServiceIds: [], available: [consultation] })),
      http.put('*/api/provider/services', () =>
        problem(400, {
          code: 'VALIDATION_ERROR',
          errors: [{ field: 'serviceIds', message: 'size must be between 0 and 200' }],
        }),
      ),
    )
    renderRoute('/provider/services')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('checkbox', { name: /initial consultation/i }))
    await user.click(screen.getByRole('button', { name: /save/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent(/no longer available/i)
    expect(alert).toHaveTextContent(ERROR_MESSAGES.VALIDATION_ERROR)
    // The alert goes away once the provider changes their choice.
    await user.click(screen.getByRole('checkbox', { name: /initial consultation/i }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('reads a service description once, as its description', async () => {
    server.use(...sessionExists(provider), servicesAre(() => ({ offeredServiceIds: [], available: [consultation] })))

    renderRoute('/provider/services')

    const box = await screen.findByRole('checkbox', { name: /initial consultation/i })
    expect(box).not.toHaveAccessibleName(/first visit/i)
    expect(box).toHaveAccessibleDescription('First visit')
  })

  it('is linked from the provider area', async () => {
    server.use(...sessionExists(provider), servicesAre(() => ({ offeredServiceIds: [], available: [] })))
    const { router } = renderRoute('/provider')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Services' }))

    expect(router.state.location.pathname).toBe('/provider/services')
  })
})
