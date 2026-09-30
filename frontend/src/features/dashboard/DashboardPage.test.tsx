import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AUTH_STORAGE_KEY } from '../auth/auth-context'
import type { RevenueSummary } from '../revenue/revenue-types'
import type { BalanceSummary } from './dashboard-api'
import { DashboardPage } from './DashboardPage'

const TOKEN = 'token-de-teste'
const HOJE = new Date(2026, 8, 15) // 15/09/2026

const saldo: BalanceSummary = {
  month: '2026-09',
  balance: 12480,
  income: 6900,
  expense: 2150,
}

const faturamentoNormal: RevenueSummary = {
  year: 2026,
  accumulated: 40500,
  limit: 81000,
  percentage: 50,
  band: 'NORMAL',
  proportionalLimit: false,
  activeMonths: null,
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()

function mockApi({
  balance = () => jsonResponse(200, saldo),
  revenue = () => jsonResponse(200, faturamentoNormal),
}: {
  balance?: () => Response | Promise<Response>
  revenue?: () => Response | Promise<Response>
} = {}) {
  fetchMock.mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/transactions/summary')) return balance()
    if (url.includes('/revenue/summary')) return revenue()
    throw new Error(`URL inesperada: ${url}`)
  })
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const session = { token: TOKEN, user: null }
  sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <DashboardPage today={HOJE} />
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

describe('DashboardPage', () => {
  it('mostra saldo, entradas e saídas do mês e o faturamento dentro do limite', async () => {
    mockApi()
    renderPage()

    expect(
      screen.getByText('Visão geral de setembro de 2026'),
    ).toBeInTheDocument()
    expect(await screen.findByText('R$ 12.480,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 6.900,00')).toBeInTheDocument()
    expect(screen.getByText('R$ 2.150,00')).toBeInTheDocument()

    expect(
      await screen.findByText(
        'R$ 40.500,00' +
          ' de ' +
          'R$ 81.000,00' +
          ' (50% do limite anual do MEI)',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText(`Dentro do limite. Margem restante: R$ 40.500,00.`),
    ).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '50',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    // Without a proportional limit there is no RN01 notice.
    expect(screen.queryByText(/Limite proporcional/)).not.toBeInTheDocument()

    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls).toContainEqual(
      expect.stringMatching(/\/transactions\/summary\?month=2026-09$/),
    )
    expect(urls).toContainEqual(
      expect.stringMatching(/\/revenue\/summary\?year=2026$/),
    )
    for (const [, init] of fetchMock.mock.calls) {
      expect(new Headers(init?.headers).get('Authorization')).toBe(
        `Bearer ${TOKEN}`,
      )
    }
  })

  it('entende a resposta do servidor exatamente como o backend a envia', async () => {
    // Field names of RevenueSummaryResponse (backend, module revenue). If the
    // contract changes on either side, this test must fail.
    const respostaDoServidor =
      '{"year":2026,"accumulated":66400.00,"limit":81000.00,"percentage":81.98,' +
      '"band":"ATTENTION","proportionalLimit":false,"activeMonths":null}'
    mockApi({
      revenue: () =>
        new Response(respostaDoServidor, {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
    })
    renderPage()

    expect(
      await screen.findByText(
        'R$ 66.400,00 de R$ 81.000,00 (81% do limite anual do MEI)',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '81',
    )
  })

  it('mostra o alerta de atenção a partir de 80% do limite (RN02)', async () => {
    mockApi({
      revenue: () =>
        jsonResponse(200, {
          ...faturamentoNormal,
          accumulated: 66400,
          percentage: 81.98,
          band: 'ATTENTION',
          proportionalLimit: false,
          activeMonths: null,
        }),
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `Atenção: você já atingiu 81% do limite anual. Margem restante: R$ 14.600,00.`,
    )
  })

  it('não arredonda o percentual para a faixa seguinte', async () => {
    mockApi({
      revenue: () =>
        jsonResponse(200, {
          ...faturamentoNormal,
          accumulated: 72891.9,
          percentage: 89.99,
          band: 'ATTENTION',
          proportionalLimit: false,
          activeMonths: null,
        }),
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'você já atingiu 89% do limite',
    )
  })

  it('mostra o alerta da faixa crítica (RN02)', async () => {
    mockApi({
      revenue: () =>
        jsonResponse(200, {
          ...faturamentoNormal,
          accumulated: 75330,
          percentage: 93,
          band: 'CRITICAL',
          proportionalLimit: false,
          activeMonths: null,
        }),
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `Faixa crítica: você já atingiu 93% do limite anual. Margem restante: R$ 5.670,00.`,
    )
  })

  it('mostra o valor excedente e orienta procurar o contador quando passa do limite', async () => {
    mockApi({
      revenue: () =>
        jsonResponse(200, {
          ...faturamentoNormal,
          accumulated: 85000,
          percentage: 104.94,
          band: 'EXCEEDED',
          proportionalLimit: false,
          activeMonths: null,
        }),
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `Limite excedido: seu faturamento passou o limite em R$ 4.000,00. Procure seu contador`,
    )
    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '100',
    )
  })

  it('sinaliza quando o limite é proporcional aos meses de atividade (RN01)', async () => {
    mockApi({
      revenue: () =>
        jsonResponse(200, {
          ...faturamentoNormal,
          accumulated: 0,
          limit: 27000,
          percentage: 0,
          proportionalLimit: true,
          activeMonths: 4,
        }),
    })
    renderPage()

    expect(
      await screen.findByText(
        'Limite proporcional a 4 meses de atividade neste ano.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByText(`Dentro do limite. Margem restante: R$ 27.000,00.`),
    ).toBeInTheDocument()
  })

  it('não mostra valores parciais quando o faturamento falha e permite tentar de novo', async () => {
    const user = userEvent.setup()
    let tentativas = 0
    mockApi({
      revenue: () =>
        ++tentativas === 1
          ? jsonResponse(500, {
              code: 'INTERNAL_ERROR',
              message: 'Erro interno.',
            })
          : jsonResponse(200, faturamentoNormal),
    })
    renderPage()

    expect(
      await screen.findByText(
        'Não foi possível carregar o faturamento agora. Tente novamente.',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    // The balance section keeps working on its own.
    expect(screen.getByText('R$ 12.480,00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByRole('progressbar')).toBeInTheDocument()
  })

  it('mostra erro amigável quando o saldo falha', async () => {
    mockApi({ balance: () => jsonResponse(500, null) })
    renderPage()

    expect(
      await screen.findByText(
        'Não foi possível carregar o saldo agora. Tente novamente.',
      ),
    ).toBeInTheDocument()
  })

  it('encerra a sessão quando o token não é mais aceito (401)', async () => {
    mockApi({
      balance: () =>
        jsonResponse(401, {
          code: 'UNAUTHENTICATED',
          message: 'Autenticação necessária.',
        }),
    })
    renderPage()

    await waitFor(() =>
      expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull(),
    )
  })
})
