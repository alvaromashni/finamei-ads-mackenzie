import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { SectionError } from '../../components/SectionError'
import { ReportExplorer } from '../report/ReportsPage'
import { clientReportsPath } from './accountant-api'
import {
  linkedClientsQueryKey,
  useLinkedClientsQuery,
} from './useLinkedClients'

const selectClass =
  'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm outline-none focus-visible:ring-2 focus-visible:ring-emerald-600'

function AccessLost({ refreshClients = false }: { refreshClients?: boolean }) {
  const queryClient = useQueryClient()

  // Access was denied mid-session, so the cached list may still show this
  // client; refresh it (RN11, UC 4a).
  useEffect(() => {
    if (refreshClients) {
      void queryClient.invalidateQueries({ queryKey: linkedClientsQueryKey })
    }
  }, [queryClient, refreshClients])

  return (
    <div
      role="alert"
      className="rounded-xl border border-amber-300 bg-amber-50 p-5 text-amber-900"
    >
      <p className="font-semibold">
        O acesso aos dados deste cliente não está mais disponível.
      </p>
      <p className="mt-2 text-sm">
        O microempreendedor pode ter revogado a consulta. Fale com ele se
        precisar do acesso novamente.
      </p>
      <Link
        to="/clientes"
        className="mt-4 inline-block rounded-lg touch:inline-flex touch:min-h-11 touch:items-center border border-amber-400 bg-white px-4 py-2 text-sm font-semibold text-amber-900 hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
      >
        Voltar para meus clientes
      </Link>
    </div>
  )
}

type ClientReportsPageProps = {
  /** Reference date; defaults to now. Useful in tests. */
  today?: Date
}

/** Read-only reports of one client for the accountant (OF17). */
export function ClientReportsPage({ today }: ClientReportsPageProps) {
  const { clientId = '' } = useParams()
  const navigate = useNavigate()
  const clientsQuery = useLinkedClientsQuery()
  const clients = clientsQuery.data
  const client = clients?.find((item) => item.id === clientId)
  const notLinked = clients !== undefined && !client

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <Link
          to="/clientes"
          className="rounded text-sm font-medium text-emerald-800 touch:inline-flex touch:min-h-11 touch:items-center hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
        >
          ← Meus clientes
        </Link>
        <h1 className="mt-2 text-2xl font-bold text-slate-900 sm:text-3xl">
          Relatórios {client ? `de ${client.name}` : 'do cliente'}
        </h1>
        <p className="mt-1 text-slate-600">
          Consulta em modo somente leitura, com acesso concedido pelo cliente.
        </p>
      </header>

      {clients && clients.length > 1 && client && (
        <div className="mt-6 sm:max-w-sm">
          <label
            htmlFor="report-client"
            className="block text-sm font-medium text-slate-800"
          >
            Cliente
          </label>
          <select
            id="report-client"
            className={selectClass}
            value={clientId}
            onChange={(event) =>
              navigate(
                `/clientes/${encodeURIComponent(event.target.value)}/relatorios`,
              )
            }
          >
            {clients.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {clientsQuery.isPending && (
        <p role="status" className="mt-6 text-slate-600">
          Carregando cliente…
        </p>
      )}

      {clientsQuery.isError && (
        <div className="mt-6">
          <SectionError
            message="Não foi possível carregar seus clientes agora. Tente novamente."
            onRetry={() => clientsQuery.refetch()}
          />
        </div>
      )}

      {notLinked && (
        <div className="mt-6">
          <AccessLost />
        </div>
      )}

      {/* Only query clients that are known to have granted access (RN11). */}
      {client && (
        <ReportExplorer
          // Start from fresh filters and export state for each client.
          key={client.id}
          basePath={clientReportsPath(client.id)}
          today={today}
          accessLost={<AccessLost refreshClients />}
        />
      )}
    </main>
  )
}
