import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { formatCurrency } from '../../lib/format'
import { AuthProvider } from '../auth/AuthProvider'
import { AUTH_STORAGE_KEY } from '../auth/auth-context'
import type { DasGuide, DasStatus } from './das-api'
import { DasPage } from './DasPage'

const TOKEN = 'token-de-teste'
const HOJE = new Date(2026, 8, 15) // 15/09/2026

// Intl uses a non-breaking space between "R$" and the value; text matchers
// normalize it to a regular space, so the expected text must match that.
const moeda = (valor: number) => formatCurrency(valor).replace(/\s/g, ' ')

/** Guides of 2026: Jan-Jun paid, Jul overdue, Aug-Dec pending. */
function guiasDoAno(ano = 2026): DasGuide[] {
  return Array.from({ length: 12 }, (_, indice) => {
    const mes = indice + 1
    const competencia = `${ano}-${String(mes).padStart(2, '0')}`
    const vencimento =
      mes === 12
        ? `${ano + 1}-01-20`
        : `${ano}-${String(mes + 1).padStart(2, '0')}-20`
    const status: DasStatus =
      mes <= 6 ? 'PAID' : mes === 7 ? 'OVERDUE' : 'PENDING'
    return {
      id: `das-${competencia}`,
      competence: competencia,
      dueDate: vencimento,
      amount: 76.9,
      status,
      paidAt:
        status === 'PAID'
          ? `${ano}-${String(mes + 1).padStart(2, '0')}-18`
          : null,
    }
  })
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()

function mockApi({
  list = () => jsonResponse(200, guiasDoAno()),
  pay = (id, init) => {
    const guia = guiasDoAno().find((g) => g.id === id)
    const { paidAt } = JSON.parse(String(init?.body))
    return jsonResponse(200, { ...guia, status: 'PAID', paidAt })
  },
}: {
  list?: (year: string) => Response | Promise<Response>
  pay?: (id: string, init?: RequestInit) => Response | Promise<Response>
} = {}) {
  fetchMock.mockImplementation(async (input, init) => {
    const url = new URL(String(input))
    const pagamento = /\/das\/([^/]+)\/payment$/.exec(url.pathname)
    if (pagamento && init?.method === 'POST') {
      return pay(decodeURIComponent(pagamento[1]), init)
    }
    if (url.pathname.endsWith('/das')) {
      return list(url.searchParams.get('year') ?? '')
    }
    throw new Error(`URL inesperada: ${url}`)
  })
}

function chamadasDePagamento() {
  return fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  sessionStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({ token: TOKEN, user: null }),
  )
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <DasPage today={HOJE} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

async function guia(nome: string) {
  const lista = await screen.findByRole('list', { name: /Guias do DAS de/ })
  const item = within(lista)
    .getAllByRole('listitem')
    .find((li) => within(li).queryByRole('heading', { name: nome }))
  if (!item) throw new Error(`Guia não encontrada: ${nome}`)
  return item
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('DasPage', () => {
  it('lista as 12 competências do ano em ordem, com vencimento, valor e situação', async () => {
    mockApi()
    renderPage()

    expect(screen.getByText('Carregando guias…')).toBeInTheDocument()

    const lista = await screen.findByRole('list', {
      name: 'Guias do DAS de 2026',
    })
    const titulos = within(lista)
      .getAllByRole('heading')
      .map((titulo) => titulo.textContent)
    expect(titulos).toEqual([
      'Janeiro de 2026',
      'Fevereiro de 2026',
      'Março de 2026',
      'Abril de 2026',
      'Maio de 2026',
      'Junho de 2026',
      'Julho de 2026',
      'Agosto de 2026',
      'Setembro de 2026',
      'Outubro de 2026',
      'Novembro de 2026',
      'Dezembro de 2026',
    ])

    const janeiro = await guia('Janeiro de 2026')
    expect(janeiro).toHaveTextContent(`Vencimento: 20/02/2026 · ${moeda(76.9)}`)
    expect(janeiro).toHaveTextContent('Paga em 18/02/2026')
    expect(
      within(janeiro).queryByRole('button', { name: /como paga/ }),
    ).not.toBeInTheDocument()

    expect(await guia('Julho de 2026')).toHaveTextContent('Vencida')
    expect(await guia('Agosto de 2026')).toHaveTextContent('Pendente')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/das\?year=2026$/)
    expect(new Headers(init?.headers).get('Authorization')).toBe(
      `Bearer ${TOKEN}`,
    )
  })

  it('resume os pagamentos do ano e alerta sobre guias vencidas', async () => {
    mockApi()
    renderPage()

    expect(await screen.findByText('6 de 12 guias pagas')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Você tem 1 guia vencida.',
    )
  })

  it('não mostra alerta quando nenhuma guia está vencida', async () => {
    mockApi({
      list: () =>
        jsonResponse(
          200,
          guiasDoAno().map((g) =>
            g.status === 'OVERDUE' ? { ...g, status: 'PENDING' } : g,
          ),
        ),
    })
    renderPage()

    expect(await screen.findByText('6 de 12 guias pagas')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('marca uma guia como paga com a data informada (OF14)', async () => {
    const user = userEvent.setup()
    mockApi()
    renderPage()

    await user.click(
      await screen.findByRole('button', {
        name: 'Marcar julho de 2026 como paga',
      }),
    )

    const formulario = screen.getByRole('form', {
      name: 'Pagamento de julho de 2026',
    })
    const data = within(formulario).getByLabelText('Data do pagamento')
    expect(data).toHaveValue('2026-09-15')
    fireEvent.change(data, { target: { value: '2026-09-10' } })
    await user.click(
      within(formulario).getByRole('button', { name: 'Confirmar pagamento' }),
    )

    expect(
      await screen.findByText('Pagamento de julho de 2026 registrado.'),
    ).toBeInTheDocument()
    const julho = await guia('Julho de 2026')
    expect(julho).toHaveTextContent('Paga em 10/09/2026')
    expect(
      within(julho).queryByRole('button', { name: /como paga/ }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('7 de 12 guias pagas')).toBeInTheDocument()
    expect(screen.queryByText(/guia vencida/)).not.toBeInTheDocument()

    const [[url, init]] = chamadasDePagamento()
    expect(String(url)).toMatch(/\/das\/das-2026-07\/payment$/)
    expect(new Headers(init?.headers).get('Authorization')).toBe(
      `Bearer ${TOKEN}`,
    )
    expect(JSON.parse(String(init?.body))).toEqual({ paidAt: '2026-09-10' })
  })

  it('não aceita data de pagamento futura', async () => {
    const user = userEvent.setup()
    mockApi()
    renderPage()

    await user.click(
      await screen.findByRole('button', {
        name: 'Marcar agosto de 2026 como paga',
      }),
    )
    const data = screen.getByLabelText('Data do pagamento')
    fireEvent.change(data, { target: { value: '2026-09-16' } })
    await user.click(
      screen.getByRole('button', { name: 'Confirmar pagamento' }),
    )

    expect(data).toHaveAccessibleDescription(
      'A data do pagamento não pode ser futura.',
    )
    expect(chamadasDePagamento()).toHaveLength(0)
  })

  it('permite desistir do pagamento', async () => {
    const user = userEvent.setup()
    mockApi()
    renderPage()

    await user.click(
      await screen.findByRole('button', {
        name: 'Marcar agosto de 2026 como paga',
      }),
    )
    await user.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByLabelText('Data do pagamento')).not.toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Marcar agosto de 2026 como paga' }),
    ).toBeInTheDocument()
    expect(chamadasDePagamento()).toHaveLength(0)
  })

  it('avisa quando a guia já tinha sido paga e recarrega a lista', async () => {
    const user = userEvent.setup()
    let listagens = 0
    mockApi({
      list: () => {
        listagens++
        return jsonResponse(200, guiasDoAno())
      },
      pay: () =>
        jsonResponse(409, {
          code: 'DAS_ALREADY_PAID',
          message: 'Esta guia já foi paga.',
        }),
    })
    renderPage()

    await user.click(
      await screen.findByRole('button', {
        name: 'Marcar agosto de 2026 como paga',
      }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Confirmar pagamento' }),
    )

    expect(
      await screen.findByText('Esta guia já foi paga.'),
    ).toBeInTheDocument()
    expect(listagens).toBe(2)
  })

  it('mostra erro amigável quando o pagamento falha, mantendo o formulário', async () => {
    const user = userEvent.setup()
    mockApi({ pay: () => jsonResponse(500, null) })
    renderPage()

    await user.click(
      await screen.findByRole('button', {
        name: 'Marcar agosto de 2026 como paga',
      }),
    )
    await user.click(
      screen.getByRole('button', { name: 'Confirmar pagamento' }),
    )

    expect(
      await screen.findByText(
        'Não foi possível concluir a operação. Tente novamente em instantes.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Data do pagamento')).toBeInTheDocument()
    expect(await guia('Agosto de 2026')).toHaveTextContent('Pendente')
  })

  it('mostra erro amigável quando as guias não carregam e permite tentar de novo', async () => {
    const user = userEvent.setup()
    let tentativas = 0
    mockApi({
      list: () =>
        ++tentativas === 1
          ? jsonResponse(500, null)
          : jsonResponse(200, guiasDoAno()),
    })
    renderPage()

    expect(
      await screen.findByText(
        'Não foi possível carregar as guias do DAS agora. Tente novamente.',
      ),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('6 de 12 guias pagas')).toBeInTheDocument()
  })

  it('consulta outro ano e informa quando não há guias', async () => {
    const user = userEvent.setup()
    mockApi({
      list: (year) => jsonResponse(200, year === '2026' ? guiasDoAno() : []),
    })
    renderPage()

    await screen.findByText('6 de 12 guias pagas')
    await user.selectOptions(screen.getByLabelText('Ano'), '2025')

    expect(
      await screen.findByText('Nenhuma guia encontrada para 2025.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Guias de 2025' }),
    ).toBeInTheDocument()
    expect(String(fetchMock.mock.calls.at(-1)?.[0])).toMatch(
      /\/das\?year=2025$/,
    )
  })
})
