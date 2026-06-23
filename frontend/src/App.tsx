import './App.scss'
import { createBrowserRouter, createRoutesFromElements, Route, RouterProvider } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { DefaultLayout } from '@shared/components'
import { UserProvider } from '@shared/contexts'
import { IndexGate, GuestRoute } from '@shared/components/-routing/RouteGuards'
import Jobs from '@pages/jobs'
import Companies from '@pages/companies'
import Login from '@pages/login'
import Register from '@pages/register'

const TestRoute = import.meta.env.DEV
  ? lazy(() => import('./pages/test'))
  : null

const router = createBrowserRouter(
  createRoutesFromElements(
    <Route path='/' element={<DefaultLayout />}>
      <Route index element={<IndexGate />} />
      <Route path='jobs' element={<Jobs />} />
      <Route path='companies' element={<Companies />} />

      <Route element={<GuestRoute />}>
        <Route path='login' element={<Login />} />
        <Route path='register' element={<Register />} />
      </Route>

      {TestRoute && (
        <Route path='/test' element={<Suspense fallback={<div>Loading...</div>}><TestRoute /></Suspense>} />
      )}
    </Route>
  )
)

const App = () => (
  <UserProvider>
    <RouterProvider router={router} />
  </UserProvider>
)

export default App
