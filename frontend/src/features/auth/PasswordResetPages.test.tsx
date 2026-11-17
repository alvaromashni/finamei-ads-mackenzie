import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import { RequestPasswordResetPage } from './RequestPasswordResetPage'
import { ResetPasswordPage } from './ResetPasswordPage'
import { AuthProvider } from './AuthProvider'

const fetchMock = vi.fn<typeof fetch>()

function renderRoutes(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider initialSession={null}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/recuperar-senha"
              element={<RequestPasswordResetPage />}
            />
            <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('recuperação de senha', () => {
  it('oferece o link Esqueci minha senha na tela de login', () => {
    renderRoutes('/login')
    expect(
      screen.getByRole('link', { name: 'Esqueci minha senha' }),
    ).toHaveAttribute('href', '/recuperar-senha')
  })

  it('confirma o envio com a mesma mensagem para qualquer e-mail', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          message:
            'Se este e-mail estiver cadastrado, enviamos um link para redefinir a senha. Ele vale por 30 minutos.',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    renderRoutes('/recuperar-senha')
    await user.type(screen.getByLabelText('E-mail'), 'naoexiste@finamei.com')
    await user.click(screen.getByRole('button', { name: 'Enviar link' }))
    expect(
      await screen.findByText(/Se este e-mail estiver cadastrado/),
    ).toBeInTheDocument()
  })

  it('recusa senha e confirmação diferentes com a frase do caso de uso', async () => {
    const user = userEvent.setup()
    renderRoutes('/redefinir-senha?token=abc')
    await user.type(screen.getByLabelText('Nova senha'), 'senha123')
    await user.type(screen.getByLabelText('Repita a senha'), 'senha124')
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }))
    expect(
      await screen.findByText(
        'As senhas não são iguais. Digite a mesma senha nos dois campos.',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('mostra o link para solicitar outro quando o token expirou', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: 'PASSWORD_RESET_EXPIRED',
          message:
            'Este link expirou. Solicite um novo link para redefinir a senha.',
        }),
        { status: 400, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    renderRoutes('/redefinir-senha?token=expirado')
    await user.type(screen.getByLabelText('Nova senha'), 'novaSenha1')
    await user.type(screen.getByLabelText('Repita a senha'), 'novaSenha1')
    await user.click(screen.getByRole('button', { name: 'Redefinir senha' }))
    expect(await screen.findByText(/Este link expirou/)).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: 'Solicitar novo link' }),
    ).toHaveAttribute('href', '/recuperar-senha')
  })
})
