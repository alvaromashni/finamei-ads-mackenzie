import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { AuthProvider } from './AuthProvider'
import {
  AUTH_STORAGE_KEY,
  type AuthSession,
  type AuthUser,
} from './auth-context'

const novaUsuaria: AuthUser = {
  id: 'u-1',
  name: 'Maria Souza',
  email: 'maria@exemplo.com',
  role: 'MEI',
  active: true,
  createdAt: '2026-09-15T12:00:00Z',
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()
const chamadas = () =>
  fetchMock.mock.calls.map(([input, init]) => ({
    url: String(input),
    body: init?.body ? JSON.parse(String(init.body)) : undefined,
  }))

function mockApi({
  register = () => jsonResponse(201, novaUsuaria),
  login = () =>
    jsonResponse(200, {
      token: 'jwt-da-maria',
      tokenType: 'Bearer',
      expiresInSeconds: 1800,
    }),
}: {
  register?: () => Response
  login?: () => Response
} = {}) {
  fetchMock.mockImplementation(async (input) => {
    const url = String(input)
    if (url.endsWith('/auth/register')) return register()
    if (url.endsWith('/auth/login')) return login()
    // Screens behind the login load data; keep those requests pending.
    return new Promise<Response>(() => {})
  })
}

function renderApp(path = '/cadastro', session: AuthSession | null = null) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={session}>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

async function preencher(
  user: ReturnType<typeof userEvent.setup>,
  valores: Partial<Record<string, string>> = {},
) {
  const {
    nome = 'Maria Souza',
    email = 'maria@exemplo.com',
    senha = 'segredo123',
    confirmacao = senha,
  } = valores
  if (nome) await user.type(screen.getByLabelText('Nome'), nome)
  if (email) await user.type(screen.getByLabelText('E-mail'), email)
  if (senha) await user.type(screen.getByLabelText('Senha'), senha)
  if (confirmacao)
    await user.type(screen.getByLabelText('Repita a senha'), confirmacao)
  await user.click(screen.getByRole('button', { name: 'Criar conta' }))
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
  mockApi()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('RegisterPage (OF01)', () => {
  it('cria a conta, entra automaticamente e abre o painel', async () => {
    const user = userEvent.setup()
    renderApp()

    await preencher(user)

    expect(
      await screen.findByRole('heading', { name: 'Painel', level: 1 }),
    ).toBeInTheDocument()
    expect(chamadas().slice(0, 2)).toEqual([
      {
        url: expect.stringMatching(/\/auth\/register$/),
        body: {
          name: 'Maria Souza',
          email: 'maria@exemplo.com',
          password: 'segredo123',
          role: 'MEI',
        },
      },
      {
        url: expect.stringMatching(/\/auth\/login$/),
        body: { email: 'maria@exemplo.com', password: 'segredo123' },
      },
    ])
    const sessao = JSON.parse(sessionStorage.getItem(AUTH_STORAGE_KEY)!)
    expect(sessao.token).toBe('jwt-da-maria')
    expect(sessao.user.name).toBe('Maria Souza')
    expect(screen.getAllByText('Olá, Maria Souza').length).toBeGreaterThan(0)
  })

  it('valida os campos sem chamar o servidor', async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getByRole('button', { name: 'Criar conta' }))

    expect(await screen.findByText('Informe o nome.')).toBeInTheDocument()
    expect(screen.getByText('Informe o e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
    expect(screen.getByText('Repita a senha.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('exige senha com 8 caracteres, letra e número (RN09)', async () => {
    const user = userEvent.setup()
    renderApp()

    await preencher(user, { senha: 'abcdefgh' })

    expect(
      await screen.findByText(
        'A senha deve ter no mínimo 8 caracteres, com pelo menos uma letra e um número.',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('avisa quando as duas senhas são diferentes', async () => {
    const user = userEvent.setup()
    renderApp()

    await preencher(user, { confirmacao: 'outra123' })

    expect(
      await screen.findByText(
        'As senhas não são iguais. Digite a mesma senha nos dois campos.',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('destaca o e-mail que já tem conta (409)', async () => {
    const user = userEvent.setup()
    mockApi({
      register: () =>
        jsonResponse(409, {
          code: 'EMAIL_ALREADY_REGISTERED',
          message: 'E-mail já cadastrado.',
        }),
    })
    renderApp()

    await preencher(user)

    expect(
      await screen.findByText(
        'Este e-mail já tem uma conta no FinaMEI. Entre com ele ou use outro e-mail.',
      ),
    ).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('E-mail')).toHaveFocus())
  })

  it('mostra no campo o erro devolvido pelo servidor (400)', async () => {
    const user = userEvent.setup()
    mockApi({
      register: () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'Os dados informados são inválidos.',
          fieldErrors: { name: 'O nome deve ter no máximo 120 caracteres.' },
        }),
    })
    renderApp()

    await preencher(user)

    expect(
      await screen.findByText('O nome deve ter no máximo 120 caracteres.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toHaveAttribute(
      'aria-invalid',
      'true',
    )
  })

  it('leva para o login com o e-mail preenchido se a entrada automática falhar', async () => {
    const user = userEvent.setup()
    mockApi({ login: () => jsonResponse(500, null) })
    renderApp()

    await preencher(user)

    expect(
      await screen.findByText(
        'Conta criada! Entre com seu e-mail e sua senha.',
      ),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Entrar no FinaMEI' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('E-mail')).toHaveValue('maria@exemplo.com')
  })

  it('liga as telas de login e de cadastro', async () => {
    const user = userEvent.setup()
    renderApp('/login')

    await user.click(screen.getByRole('link', { name: 'Criar conta' }))
    expect(
      screen.getByRole('heading', { name: 'Criar conta' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Entrar' }))
    expect(
      screen.getByRole('heading', { name: 'Entrar no FinaMEI' }),
    ).toBeInTheDocument()
  })

  it('manda para dentro do sistema quem já está logado', () => {
    renderApp('/cadastro', { token: 'token', user: null })

    expect(
      screen.getByRole('heading', { name: 'Painel', level: 1 }),
    ).toBeInTheDocument()
  })
})
