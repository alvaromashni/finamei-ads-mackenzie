import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { LoginPage } from './features/auth/LoginPage'
import { RequestPasswordResetPage } from './features/auth/RequestPasswordResetPage'
import { ResetPasswordPage } from './features/auth/ResetPasswordPage'
import { ClientReportsPage } from './features/accountant/ClientReportsPage'
import { AccountantAccessPage } from './features/accountant-access/AccountantAccessPage'
import { ClientsPage } from './features/accountant/ClientsPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { HomeRedirect, RequireRole } from './features/auth/role-routes'
import { CategoriesPage } from './features/category/CategoriesPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { DasPage } from './features/das/DasPage'
import { AdminUsersPage } from './features/admin/AdminUsersPage'
import { ProfilePage } from './features/profile/ProfilePage'
import { ReportsPage } from './features/report/ReportsPage'
import { TransactionsPage } from './features/transaction/TransactionsPage'

// Administrador usa as telas do MEI e a tela de contas.
const MEI_ROLES = ['MEI', 'ADMIN'] as const
const ACCOUNTANT_ONLY = ['ACCOUNTANT'] as const

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/cadastro" element={<RegisterPage />} />
      <Route path="/recuperar-senha" element={<RequestPasswordResetPage />} />
      <Route path="/redefinir-senha" element={<ResetPasswordPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomeRedirect />} />
        <Route
          element={
            <RequireRole roles={MEI_ROLES}>
              <Outlet />
            </RequireRole>
          }
        >
          <Route path="/painel" element={<DashboardPage />} />
          <Route path="/lancamentos" element={<TransactionsPage />} />
          <Route path="/das" element={<DasPage />} />
          <Route path="/relatorios" element={<ReportsPage />} />
          <Route path="/categorias" element={<CategoriesPage />} />
          <Route path="/contador" element={<AccountantAccessPage />} />
        </Route>
        <Route
          element={
            <RequireRole roles={ACCOUNTANT_ONLY}>
              <Outlet />
            </RequireRole>
          }
        >
          <Route path="/clientes" element={<ClientsPage />} />
          <Route
            path="/clientes/:clientId/relatorios"
            element={<ClientReportsPage />}
          />
        </Route>
        <Route path="/perfil" element={<ProfilePage />} />
        <Route
          element={
            <RequireRole roles={['ADMIN']}>
              <Outlet />
            </RequireRole>
          }
        >
          <Route path="/admin/usuarios" element={<AdminUsersPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
