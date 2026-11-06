import { createBrowserRouter, type RouteObject } from 'react-router'
import { RequireRole } from './auth/RequireRole'
import { AppLayout } from './components/AppLayout'
import { AdminHomePage } from './features/admin/AdminHomePage'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { HomePage } from './features/home/HomePage'
import { ProfilePage } from './features/profile/ProfilePage'

export const routes: RouteObject[] = [
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/login', element: <LoginPage /> },
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
    ],
  },
]

export const router = createBrowserRouter(routes)
