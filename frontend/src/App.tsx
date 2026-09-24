import { Navigate, Route, Routes } from 'react-router-dom'

const pages = {
  login: {
    title: 'Entrar no FinaMEI',
    description: 'Acesse sua conta para acompanhar as finanças do seu negócio.',
  },
  painel: {
    title: 'Painel financeiro',
    description: 'Resumo de saldo, receitas, despesas e faturamento anual.',
  },
  lancamentos: {
    title: 'Lançamentos',
    description: 'Registre e acompanhe as movimentações do seu negócio.',
  },
  das: {
    title: 'Controle do DAS',
    description: 'Consulte as guias mensais e mantenha os pagamentos em dia.',
  },
}

type PageProps = (typeof pages)[keyof typeof pages]

function PlaceholderPage({ title, description }: PageProps) {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl items-center px-6 py-16">
      <section className="w-full rounded-2xl border border-emerald-100 bg-white p-8 shadow-sm">
        <span className="text-sm font-semibold uppercase tracking-wider text-emerald-700">
          FinaMEI
        </span>
        <h1 className="mt-3 text-3xl font-bold text-slate-900">{title}</h1>
        <p className="mt-3 text-slate-600">{description}</p>
        <p className="mt-8 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
          Estrutura inicial pronta para a implementação desta funcionalidade.
        </p>
      </section>
    </main>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<PlaceholderPage {...pages.login} />} />
      <Route path="/painel" element={<PlaceholderPage {...pages.painel} />} />
      <Route
        path="/lancamentos"
        element={<PlaceholderPage {...pages.lancamentos} />}
      />
      <Route path="/das" element={<PlaceholderPage {...pages.das} />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

export default App
