import { Link } from 'react-router-dom'
import { SectionError } from '../../components/SectionError'
import { useLinkedClientsQuery } from './useLinkedClients'

const dateFormatter = new Intl.DateTimeFormat('pt-BR')

/** Accountant area: clients that granted read-only access (OF17, RN11). */
export function ClientsPage() {
  const clientsQuery = useLinkedClientsQuery()
  const clients = clientsQuery.data

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Meus clientes
        </h1>
        <p className="mt-1 text-slate-600">
          Microempreendedores que liberaram a consulta dos dados para você.
        </p>
      </header>

      <div className="mt-6">
        {clientsQuery.isPending && (
          <p role="status" className="text-slate-600">
            Carregando clientes…
          </p>
        )}

        {clientsQuery.isError && (
          <SectionError
            message="Não foi possível carregar seus clientes agora. Tente novamente."
            onRetry={() => clientsQuery.refetch()}
          />
        )}

        {clients?.length === 0 && (
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <p className="font-semibold text-slate-900">
              Nenhum cliente liberou acesso ainda.
            </p>
            <p className="mt-2 text-slate-600">
              Peça ao microempreendedor para conceder acesso de consulta ao seu
              e-mail no FinaMEI. Assim que ele liberar, o cliente aparece aqui.
            </p>
          </div>
        )}

        {clients && clients.length > 0 && (
          <ul className="grid gap-3 sm:grid-cols-2">
            {clients.map((client) => (
              <li
                key={client.id}
                className="flex min-w-0 flex-col gap-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <div className="min-w-0">
                  <p className="font-semibold text-slate-900">{client.name}</p>
                  <p className="break-all text-sm text-slate-600">
                    {client.email}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    Acesso liberado em{' '}
                    {dateFormatter.format(new Date(client.grantedAt))}
                  </p>
                </div>
                <Link
                  to={`/clientes/${encodeURIComponent(client.id)}/relatorios`}
                  aria-label={`Ver relatórios de ${client.name}`}
                  className="self-start rounded-lg touch:inline-flex touch:min-h-11 touch:items-center touch:justify-center bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
                >
                  Ver relatórios
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  )
}
