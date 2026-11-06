import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { PageLoader } from './components/ui'
import { isStaff, useAuth } from './lib/auth'
import type { Role } from './lib/types'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ClaimsPage from './pages/ClaimsPage'
import NotFoundPage from './pages/NotFoundPage'

// Code-split heavier screens; the dashboard pulls in the charting library.
const NewClaimPage = lazy(() => import('./pages/NewClaimPage'))
const ClaimDetailPage = lazy(() => import('./pages/ClaimDetailPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const PoliciesPage = lazy(() => import('./pages/PoliciesPage'))
const UsersPage = lazy(() => import('./pages/UsersPage'))

function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const { user, loading } = useAuth()
  if (loading) return <PageLoader />
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to="/" replace />
  return <>{children}</>
}

function Home() {
  const { user } = useAuth()
  return <Navigate to={isStaff(user) ? '/dashboard' : '/claims'} replace />
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Home />} />
        <Route path="dashboard" element={<RequireAuth roles={['ADJUSTER', 'MANAGER', 'ADMIN']}><DashboardPage /></RequireAuth>} />
        <Route path="claims" element={<ClaimsPage />} />
        <Route path="claims/new" element={<RequireAuth roles={['CUSTOMER']}><NewClaimPage /></RequireAuth>} />
        <Route path="claims/:id" element={<ClaimDetailPage />} />
        <Route path="policies" element={<PoliciesPage />} />
        <Route path="users" element={<RequireAuth roles={['ADMIN']}><UsersPage /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <Suspense fallback={<PageLoader />}>
      <AppRoutes />
    </Suspense>
  )
}
