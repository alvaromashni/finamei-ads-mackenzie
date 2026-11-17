import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../features/auth/AuthProvider'
import type { AuthSession } from '../features/auth/auth-context'
import { DocumentTitle } from './DocumentTitle'
import { getPageTitle } from './page-titles'

const fetchMock = vi.fn<typeof fetch>()

function renderApp(path: string, session: AuthSession | null) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={session}>
        <MemoryRouter initialEntries={[path]}>
          <DocumentTitle />
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const logada: AuthSession = { token: 'token', user: null }

beforeEach(() => {
  fetchMock.mockReset()
  // Screens load data on mount; keep requests pending unless a test says otherwise.
  fetchMock.mockImplementation(() => new Promise(() => {}))
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('título da aba do navegador', () => {
  it('muda conforme a tela', async () => {
    const user = userEvent.setup()
    renderApp('/painel', logada)
    expect(document.title).toBe('Painel · FinaMEI')

    await user.click(screen.getByRole('link', { name: 'Relatórios' }))

    await waitFor(() => expect(document.title).toBe('Relatórios · FinaMEI'))
  })

  it('tem um título para cada tela', () => {
    expect(getPageTitle('/login')).toBe('Entrar · FinaMEI')
    expect(getPageTitle('/cadastro')).toBe('Criar conta · FinaMEI')
    expect(getPageTitle('/das')).toBe('Controle do DAS · FinaMEI')
    expect(getPageTitle('/clientes/abc/relatorios')).toBe(
      'Relatórios do cliente · FinaMEI',
    )
    expect(getPageTitle('/qualquer-coisa')).toBe('FinaMEI')
  })
})

describe('navegação por teclado e leitor de tela', () => {
  it('oferece o atalho para pular direto ao conteúdo', () => {
    renderApp('/painel', logada)

    const atalho = screen.getByRole('link', { name: 'Pular para o conteúdo' })
    expect(atalho).toHaveAttribute('href', '#conteudo')
    expect(document.getElementById('conteudo')).toContainElement(
      screen.getByRole('heading', { name: 'Painel', level: 1 }),
    )
  })

  it('leva o foco para o título da nova tela ao navegar pelo menu', async () => {
    const user = userEvent.setup()
    renderApp('/painel', logada)

    await user.click(screen.getByRole('link', { name: 'Meu perfil' }))

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Meu perfil', level: 1 }),
      ).toHaveFocus(),
    )
  })

  it('abre e fecha o menu pelo botão "Menu" (telas pequenas)', async () => {
    const user = userEvent.setup()
    renderApp('/painel', logada)

    const botao = screen.getByRole('button', { name: 'Menu' })
    expect(botao).toHaveAttribute('aria-expanded', 'false')
    expect(botao).toHaveAttribute('aria-controls', 'menu-principal')

    await user.click(botao)
    expect(botao).toHaveAttribute('aria-expanded', 'true')

    // Choosing a screen closes the menu again.
    await user.click(screen.getByRole('link', { name: 'Relatórios' }))
    await waitFor(() => expect(botao).toHaveAttribute('aria-expanded', 'false'))
  })
})

describe('sessão expirada', () => {
  it('explica na tela de login que a sessão expirou', () => {
    vi.useFakeTimers()
    renderApp('/painel', { ...logada, expiresAt: Date.now() + 60_000 })

    act(() => {
      vi.advanceTimersByTime(60_000)
    })

    expect(
      screen.getByRole('heading', { name: 'Entrar no FinaMEI' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Sua sessão expirou. Entre novamente para continuar.'),
    ).toBeInTheDocument()
  })

  it('também avisa quando o servidor recusa o token (401)', async () => {
    fetchMock.mockImplementation(
      async () =>
        new Response(
          JSON.stringify({ code: 'UNAUTHENTICATED', message: 'x' }),
          { status: 401, headers: { 'Content-Type': 'application/json' } },
        ),
    )
    renderApp('/painel', logada)

    expect(
      await screen.findByText(
        'Sua sessão expirou. Entre novamente para continuar.',
      ),
    ).toBeInTheDocument()
  })

  it('não mostra o aviso quando a pessoa sai por conta própria', async () => {
    const user = userEvent.setup()
    renderApp('/painel', logada)

    await user.click(screen.getByRole('button', { name: 'Sair' }))

    expect(
      screen.getByRole('heading', { name: 'Entrar no FinaMEI' }),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Sua sessão expirou/)).not.toBeInTheDocument()
  })
})
