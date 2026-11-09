import { createBrowserRouter, type RouteObject } from 'react-router'
import { RequireRole } from './auth/RequireRole'
import { AppLayout } from './components/AppLayout'
import { AdminHomePage } from './features/admin/AdminHomePage'
import { AdminProvidersPage } from './features/admin/AdminProvidersPage'
import { AdminServicesPage } from './features/admin/AdminServicesPage'
import { AcceptInvitePage } from './features/auth/AcceptInvitePage'
import { ForgotPasswordPage } from './features/auth/ForgotPasswordPage'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { ResetPasswordPage } from './features/auth/ResetPasswordPage'
import { HomePage } from './features/home/HomePage'
import { ProfilePage } from './features/profile/ProfilePage'

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      { path: '/accept-invite', element: <AcceptInvitePage /> },
      {
        path: '/profile',
        element: (
          <RequireRole>
            <ProfilePage />
          </RequireRole>
        ),
      },
      {
        path: '/admin',
        element: (
          <RequireRole role="ADMIN">
            <AdminHomePage />
          </RequireRole>
        ),
      },
      {
        path: '/admin/providers',
        element: (
          <RequireRole role="ADMIN">
            <AdminProvidersPage />
          </RequireRole>
        ),
      },
      {
        path: '/admin/services',
        element: (
          <RequireRole role="ADMIN">
            <AdminServicesPage />
          </RequireRole>
        ),
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
