import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderRoute } from '../../test/render'

describe('home route', () => {
  it('renders the placeholder home page at /', async () => {
    renderRoute('/')

    expect(await screen.findByRole('heading', { level: 1, name: /appointment prep/i })).toBeInTheDocument()
  })
})
