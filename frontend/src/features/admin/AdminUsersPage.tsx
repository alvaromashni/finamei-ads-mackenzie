import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SectionError } from '../../components/SectionError'
import { useAuth } from '../auth/useAuth'
import { useAuthorizedFetch } from '../auth/useAuthorizedFetch'
import {
  fetchAdminUsers,
  updateAdminUserStatus,
  type AdminUser,
} from './admin-api'

const roleLabels: Record<AdminUser['role'], string> = {
  MEI: 'Microempreendedor',
  ACCOUNTANT: 'Contador',
  ADMIN: 'Administrador',
}

/** Lista as contas e permite inativar ou reativar (UC-001, fluxo 4c). */
export function AdminUsersPage() {
  const { token } = useAuth()
  const authorizedFetch = useAuthorizedFetch()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => authorizedFetch((current) => fetchAdminUsers(current)),
    enabled: Boolean(token),
  })
  const mutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      authorizedFetch((current) => updateAdminUserStatus(current, id, active)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })

  return (
    <main className="mx-auto w-full max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <header>
        <h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">
          Contas
        </h1>
        <p className="mt-1 text-slate-600">
          Inative uma conta para impedir o login. A pessoa vê a orientação para
          falar com o suporte.
        </p>
      </header>

      <div className="mt-6">
        {query.isPending && (
          <p role="status" className="text-slate-600">
            Carregando contas…
          </p>
        )}
        {query.isError && (
          <SectionError
            message="Não foi possível carregar as contas agora. Tente novamente."
            onRetry={() => query.refetch()}
          />
        )}
        {query.data && (
          <ul className="divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
            {query.data.content.map((account) => (
              <li
                key={account.id}
                className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0">
                  <p className="font-semibold text-slate-900">{account.name}</p>
                  <p className="break-all text-sm text-slate-600">
                    {account.email}
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    {roleLabels[account.role]} ·{' '}
                    {account.active ? 'Ativa' : 'Inativa'}
                  </p>
                </div>
                <button
                  type="button"
                  disabled={mutation.isPending}
                  onClick={() =>
                    mutation.mutate({
                      id: account.id,
                      active: !account.active,
                    })
                  }
                  className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {account.active ? 'Inativar' : 'Reativar'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </main>
  )
}
