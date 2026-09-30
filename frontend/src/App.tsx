import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { LoginPage } from './features/auth/LoginPage'
import { ClientReportsPage } from './features/accountant/ClientReportsPage'
import { ClientsPage } from './features/accountant/ClientsPage'
import { RequireAuth } from './features/auth/RequireAuth'
import { HomeRedirect, RequireRole } from './features/auth/role-routes'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { DasPage } from './features/das/DasPage'
import { ProfilePage } from './features/profile/ProfilePage'
import { ReportsPage } from './features/report/ReportsPage'
import { TransactionsPage } from './features/transaction/TransactionsPage'

// Admin screens are not built yet; admins get the MEI screens for now.
const MEI_ROLES = ['MEI', 'ADMIN'] as const
const ACCOUNTANT_ONLY = ['ACCOUNTANT'] as const

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
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
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
