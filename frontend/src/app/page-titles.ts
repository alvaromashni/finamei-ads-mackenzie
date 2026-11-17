const APP_NAME = 'FinaMEI'

/** Title of each screen, shown in the browser tab. */
const titles: [RegExp, string][] = [
  [/^\/login$/, 'Entrar'],
  [/^\/cadastro$/, 'Criar conta'],
  [/^\/recuperar-senha$/, 'Esqueci minha senha'],
  [/^\/redefinir-senha$/, 'Redefinir senha'],
  [/^\/admin\/usuarios$/, 'Contas'],
  [/^\/painel$/, 'Painel'],
  [/^\/lancamentos$/, 'Lançamentos'],
  [/^\/das$/, 'Controle do DAS'],
  [/^\/relatorios$/, 'Relatórios'],
  [/^\/categorias$/, 'Categorias'],
  [/^\/contador$/, 'Acesso do contador'],
  [/^\/perfil$/, 'Meu perfil'],
  [/^\/clientes$/, 'Meus clientes'],
  [/^\/clientes\/[^/]+\/relatorios$/, 'Relatórios do cliente'],
]

/** "Painel · FinaMEI"; unknown paths get just the app name. */
export function getPageTitle(pathname: string) {
  const title = titles.find(([pattern]) => pattern.test(pathname))?.[1]
  return title ? `${title} · ${APP_NAME}` : APP_NAME
}
