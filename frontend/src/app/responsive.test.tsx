import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../features/auth/AuthProvider'
import type { AuthSession } from '../features/auth/auth-context'
import { TransactionItem } from '../features/transaction/TransactionItem'

/*
 * jsdom does not apply media queries, so these tests guard the classes
 * behind the responsive layout (ONF08). The visual pass at 360 px, tablet
 * and desktop is described in docs/responsividade.md.
 */

const TOUCH_CLASSES = ['touch:min-h-11', 'touch:items-center']

const mei: AuthSession = {
  token: 'token',
  user: {
    id: 'u1',
    name: 'Maria',
    email: 'maria@example.com',
    role: 'MEI',
    active: true,
    createdAt: '2026-01-10T12:00:00Z',
  },
}

function renderWithProviders(ui: React.ReactNode, path = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={path === '/login' ? null : mei}>
        <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(() => new Promise(() => {})),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('responsividade (ONF08)', () => {
  it('aumenta a área de toque dos links do menu e das ações do painel', () => {
    renderWithProviders(<App />, '/painel')

    const nav = screen.getByRole('navigation', { name: 'Navegação principal' })
    for (const link of within(nav).getAllByRole('link')) {
      expect(link).toHaveClass(...TOUCH_CLASSES)
    }
    expect(screen.getByRole('link', { name: '+ Nova receita' })).toHaveClass(
      ...TOUCH_CLASSES,
    )
    expect(screen.getByRole('link', { name: '+ Nova despesa' })).toHaveClass(
      ...TOUCH_CLASSES,
    )
  })

  it('aumenta a área de toque do link de cadastro no login', () => {
    renderWithProviders(<App />, '/login')
    expect(screen.getByRole('link', { name: 'Criar conta' })).toHaveClass(
      ...TOUCH_CLASSES,
    )
  })

  it('empilha o valor abaixo da descrição do lançamento em telas estreitas', () => {
    renderWithProviders(
      <ul>
        <TransactionItem
          transaction={{
            id: 't1',
            type: 'INCOME',
            amount: 150,
            date: '2026-10-28',
            description: 'Venda balcão',
            category: { id: 'c1', name: 'Vendas' },
          }}
          isEditing={false}
          onEdit={() => {}}
          onDeleted={() => {}}
        />
      </ul>,
    )

    const row = screen.getByText('Venda balcão').parentElement?.parentElement
    expect(row).toHaveClass('flex-col', 'sm:flex-row')
  })
})
