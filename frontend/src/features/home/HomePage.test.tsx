import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../../router'

describe('home route', () => {
  it('renders the placeholder home page at /', async () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { level: 1, name: /appointment prep/i })).toBeInTheDocument()
  })
})
