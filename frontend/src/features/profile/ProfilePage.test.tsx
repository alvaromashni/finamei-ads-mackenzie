import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { AUTH_STORAGE_KEY, type AuthUser } from '../auth/auth-context'
import { ProfilePage } from './ProfilePage'

const TOKEN = 'token-de-teste'

const perfilAtual: AuthUser = {
  id: '6c1f7a3e-0000-4000-8000-000000000001',
  name: 'Maria Souza',
  email: 'maria@exemplo.com',
  role: 'MEI',
  active: true,
  createdAt: '2026-09-01T12:00:00Z',
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={{ token: TOKEN, user: perfilAtual }}>
        <ProfilePage />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  sessionStorage.clear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ProfilePage', () => {
  it('carrega o formulário preenchido com os dados atuais do perfil', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, perfilAtual))

    renderPage()

    expect(screen.getByText('Carregando seus dados…')).toBeInTheDocument()
    expect(await screen.findByLabelText('Nome')).toHaveValue('Maria Souza')
    expect(screen.getByLabelText('E-mail')).toHaveValue('maria@exemplo.com')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/users\/me$/)
    expect(new Headers(init?.headers).get('Authorization')).toBe(
      `Bearer ${TOKEN}`,
    )
  })

  it('salva a alteração válida, mantém o valor na tela e atualiza a sessão local', async () => {
    const user = userEvent.setup()
    const atualizado = { ...perfilAtual, name: 'Maria S. Oliveira' }
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, perfilAtual))
      .mockResolvedValueOnce(jsonResponse(200, atualizado))

    renderPage()

    const nome = await screen.findByLabelText('Nome')
    await user.clear(nome)
    await user.type(nome, 'Maria S. Oliveira')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(
      await screen.findByText('Perfil atualizado com sucesso.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Nome')).toHaveValue('Maria S. Oliveira')

    const [, init] = fetchMock.mock.calls[1]
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toEqual({
      name: 'Maria S. Oliveira',
      email: 'maria@exemplo.com',
      meiOpeningDate: null,
      clearMeiOpeningDate: true,
    })

    const sessao = JSON.parse(sessionStorage.getItem(AUTH_STORAGE_KEY) ?? '{}')
    expect(sessao.user.name).toBe('Maria S. Oliveira')
    expect(sessao.token).toBe(TOKEN)
  })

  it('mostra mensagens de validação compreensíveis sem chamar a API', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse(200, perfilAtual))

    renderPage()

    const nome = await screen.findByLabelText('Nome')
    const email = screen.getByLabelText('E-mail')
    await user.clear(nome)
    await user.clear(email)
    await user.type(email, 'email-invalido')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(await screen.findByText('Informe o nome.')).toBeInTheDocument()
    expect(screen.getByText('Informe um e-mail válido.')).toBeInTheDocument()
    expect(nome).toHaveAttribute('aria-invalid', 'true')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('destaca o campo de e-mail quando ele já está em uso (409)', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, perfilAtual))
      .mockResolvedValueOnce(
        jsonResponse(409, {
          code: 'EMAIL_ALREADY_REGISTERED',
          message: 'E-mail já cadastrado.',
          fieldErrors: {},
          timestamp: '2026-09-29T12:00:00Z',
        }),
      )

    renderPage()

    const email = await screen.findByLabelText('E-mail')
    await user.clear(email)
    await user.type(email, 'outra@exemplo.com')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(
      await screen.findByText(
        'Este e-mail já está em uso por outra conta. Informe um e-mail diferente.',
      ),
    ).toBeInTheDocument()
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveValue('outra@exemplo.com')
  })

  it('exibe os erros de campo retornados pelo backend (400)', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, perfilAtual))
      .mockResolvedValueOnce(
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'Os dados informados são inválidos.',
          fieldErrors: { name: 'O nome deve ter no máximo 120 caracteres.' },
        }),
      )

    renderPage()

    const nome = await screen.findByLabelText('Nome')
    await user.type(nome, ' Jr.')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    expect(
      await screen.findByText('O nome deve ter no máximo 120 caracteres.'),
    ).toBeInTheDocument()
  })

  it('mostra erro amigável e permite tentar de novo quando o carregamento falha', async () => {
    const user = userEvent.setup()
    fetchMock
      .mockResolvedValueOnce(jsonResponse(500, null))
      .mockResolvedValueOnce(jsonResponse(200, perfilAtual))

    renderPage()

    expect(
      await screen.findByText(
        'Não foi possível carregar seu perfil. Tente novamente.',
      ),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))

    await waitFor(() =>
      expect(screen.getByLabelText('Nome')).toHaveValue('Maria Souza'),
    )
  })

  it('desabilita o botão de salvar enquanto não há alterações', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, perfilAtual))

    renderPage()

    await screen.findByLabelText('Nome')
    expect(
      screen.getByRole('button', { name: 'Salvar alterações' }),
    ).toBeDisabled()
  })
})
