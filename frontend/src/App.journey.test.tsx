import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'

/**
 * Jornada completa do MEI com a API simulada em memória: entrar, ver o
 * faturamento no painel, registrar uma receita e ver a faixa mudar.
 * Os formatos das respostas seguem os DTOs do backend (LoginResponse,
 * BalanceSummaryResponse, RevenueSummaryResponse, TransactionResponse e
 * PageResponse).
 */
const LIMIT = 81000
const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function bandFor(accumulated: number) {
  if (accumulated >= LIMIT) return 'EXCEEDED'
  if (accumulated >= LIMIT * 0.9) return 'CRITICAL'
  if (accumulated >= LIMIT * 0.8) return 'ATTENTION'
  return 'NORMAL'
}

function mockBackend(initialAccumulated: number) {
  let accumulated = initialAccumulated
  const transactions: unknown[] = []

  fetchMock.mockImplementation(async (input, init) => {
    const url = new URL(String(input))
    const method = init?.method ?? 'GET'
    const path = url.pathname.replace(/^.*\/api\/v1/, '')

    if (path === '/auth/login' && method === 'POST') {
      return jsonResponse(200, {
        token: 'jwt-de-teste',
        tokenType: 'Bearer',
        expiresInSeconds: 1800,
      })
    }
    if (path === '/transactions/summary') {
      return jsonResponse(200, {
        month: url.searchParams.get('month'),
        balance: accumulated,
        income: accumulated,
        expense: 0,
      })
    }
    if (path === '/revenue/summary') {
      return jsonResponse(200, {
        year: Number(url.searchParams.get('year')),
        accumulated,
        limit: LIMIT,
        percentage: Math.round((accumulated / LIMIT) * 10000) / 100,
        band: bandFor(accumulated),
        proportionalLimit: false,
        activeMonths: null,
      })
    }
    if (path === '/categories') {
      return jsonResponse(200, [
        { id: 'cat-vendas', name: 'Vendas', type: 'INCOME', active: true },
      ])
    }
    if (path === '/transactions' && method === 'POST') {
      const body = JSON.parse(String(init?.body))
      accumulated += body.amount
      const created = {
        id: `t-${transactions.length + 1}`,
        type: body.type,
        amount: body.amount,
        date: body.date,
        description: body.description,
        category: { id: body.categoryId, name: 'Vendas' },
      }
      transactions.unshift(created)
      return jsonResponse(201, created)
    }
    if (path === '/transactions') {
      return jsonResponse(200, {
        content: transactions,
        number: 0,
        size: 20,
        totalElements: transactions.length,
        totalPages: transactions.length ? 1 : 0,
      })
    }
    throw new Error(`Rota inesperada: ${method} ${path}`)
  })
}

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={null}>
        <MemoryRouter initialEntries={['/login']}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Jornada do MEI', () => {
  it('entra, registra uma receita pelo painel e vê o faturamento subir de faixa', async () => {
    const user = userEvent.setup()
    mockBackend(40500)
    renderApp()

    await user.type(screen.getByLabelText('E-mail'), 'maria@exemplo.com')
    await user.type(screen.getByLabelText('Senha'), 'senha-secreta1')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(
      await screen.findByText(
        'R$ 40.500,00 de R$ 81.000,00 (50% do limite anual do MEI)',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText(/Dentro do limite/)).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '+ Nova receita' }))
    await user.type(await screen.findByLabelText('Valor (R$)'), '30000')
    await screen.findByRole('option', { name: 'Vendas' })
    await user.selectOptions(screen.getByLabelText('Categoria'), 'Vendas')
    await user.type(screen.getByLabelText('Descrição'), 'Encomenda grande')
    await user.click(screen.getByRole('button', { name: 'Registrar receita' }))

    expect(
      await screen.findByText('Receita registrada com sucesso.'),
    ).toBeInTheDocument()
    expect(await screen.findByText('Encomenda grande')).toBeInTheDocument()

    const nav = screen.getByRole('navigation', { name: 'Navegação principal' })
    await user.click(within(nav).getByRole('link', { name: 'Painel' }))

    expect(
      await screen.findByText(
        'R$ 70.500,00 de R$ 81.000,00 (87% do limite anual do MEI)',
      ),
    ).toBeInTheDocument()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Atenção: você já atingiu 87% do limite anual.',
    )
  })
})
