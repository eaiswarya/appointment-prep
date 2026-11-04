import { createBrowserRouter, type RouteObject } from 'react-router'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { HomePage } from './features/home/HomePage'

export const routes: RouteObject[] = [
  { path: '/', element: <HomePage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/login', element: <LoginPage /> },
]

export const router = createBrowserRouter(routes)
