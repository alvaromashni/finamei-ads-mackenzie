import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { AUTH_STORAGE_KEY, type AuthSession } from './auth-context'
import { LoginPage } from './LoginPage'
import { RequireAuth } from './RequireAuth'
import { HomeRedirect } from './role-routes'

const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderLogin(path = '/login', session: AuthSession | null = null) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={session}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/" element={<HomeRedirect />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/clientes" element={<h1>Meus clientes</h1>} />
            <Route
              path="/painel"
              element={
                <RequireAuth>
                  <h1>Painel financeiro</h1>
                </RequireAuth>
              }
            />
            <Route
              path="/das"
              element={
                <RequireAuth>
                  <h1>Controle do DAS</h1>
                </RequireAuth>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

/** Unsigned JWT carrying only the claims the interface reads. */
function tokenWithRole(role: string) {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '')
  return `${encode({ alg: 'HS256' })}.${encode({ sub: '1', role })}.assinatura`
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('E-mail'), email)
  await user.type(screen.getByLabelText('Senha'), password)
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  return user
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('LoginPage', () => {
  it('entra com credenciais válidas, guarda a sessão e abre o painel', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        token: 'jwt-de-teste',
        tokenType: 'Bearer',
        expiresInSeconds: 1800,
      }),
    )
    renderLogin()

    await fillAndSubmit('maria@exemplo.com', 'senha-secreta')

    expect(
      await screen.findByRole('heading', { name: 'Painel financeiro' }),
    ).toBeInTheDocument()

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/auth\/login$/)
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      email: 'maria@exemplo.com',
      password: 'senha-secreta',
    })

    const stored = JSON.parse(sessionStorage.getItem(AUTH_STORAGE_KEY)!)
    expect(stored.token).toBe('jwt-de-teste')
    expect(stored.expiresAt).toBeGreaterThan(Date.now())
  })

  it('leva o contador para a lista de clientes depois de entrar', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        token: tokenWithRole('ACCOUNTANT'),
        tokenType: 'Bearer',
        expiresInSeconds: 1800,
      }),
    )
    renderLogin()

    await fillAndSubmit('contador@exemplo.com', 'senha-secreta')

    expect(
      await screen.findByRole('heading', { name: 'Meus clientes' }),
    ).toBeInTheDocument()
  })

  it('volta para a tela que o usuário tentou abrir antes de entrar', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        token: 'jwt-de-teste',
        tokenType: 'Bearer',
        expiresInSeconds: 1800,
      }),
    )
    renderLogin('/das')

    await fillAndSubmit('maria@exemplo.com', 'senha-secreta')

    expect(
      await screen.findByRole('heading', { name: 'Controle do DAS' }),
    ).toBeInTheDocument()
  })

  it('redireciona para o painel quem já está logado', () => {
    renderLogin('/login', { token: 'token', user: null })

    expect(
      screen.getByRole('heading', { name: 'Painel financeiro' }),
    ).toBeInTheDocument()
  })

  it('mostra mensagens de validação compreensíveis sem chamar a API', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Informe o e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
    expect(screen.getByLabelText('E-mail')).toHaveAttribute(
      'aria-invalid',
      'true',
    )

    await user.type(screen.getByLabelText('E-mail'), 'maria')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(
      await screen.findByText('Informe um e-mail válido.'),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('avisa quando e-mail ou senha estão incorretos e limpa a senha', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, {
        code: 'INVALID_CREDENTIALS',
        message: 'E-mail ou senha inválidos.',
      }),
    )
    renderLogin()

    await fillAndSubmit('maria@exemplo.com', 'senha-errada')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'E-mail ou senha inválidos.',
    )
    expect(screen.getByLabelText('Senha')).toHaveValue('')
    expect(screen.getByLabelText('E-mail')).toHaveValue('maria@exemplo.com')
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('avisa quando a conta está bloqueada por tentativas (423) sem encerrar a tela', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(423, {
        code: 'ACCOUNT_LOCKED',
        message:
          'Muitas tentativas de login malsucedidas. Tente novamente mais tarde.',
      }),
    )
    renderLogin()

    await fillAndSubmit('maria@exemplo.com', 'senha-secreta')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas tentativas de login malsucedidas. Tente novamente mais tarde.',
    )
    expect(sessionStorage.getItem(AUTH_STORAGE_KEY)).toBeNull()
  })

  it('exibe os erros de campo retornados pelo backend (400)', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'VALIDATION_ERROR',
        message: 'Os dados informados são inválidos.',
        fieldErrors: { email: 'Informe um e-mail válido.' },
      }),
    )
    renderLogin()

    await fillAndSubmit('maria@exemplo.com', 'senha-secreta')

    expect(
      await screen.findByText('Informe um e-mail válido.'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByLabelText('E-mail')).toHaveAttribute(
        'aria-invalid',
        'true',
      ),
    )
    // The field is disabled while saving; it must still get the focus back.
    await waitFor(() => expect(screen.getByLabelText('E-mail')).toHaveFocus())
  })

  it('mostra erro amigável quando o servidor não responde', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderLogin()

    await fillAndSubmit('maria@exemplo.com', 'senha-secreta')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Não foi possível conectar ao servidor. Verifique sua conexão.',
    )
  })
})
