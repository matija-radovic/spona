import './App.scss'
import { createBrowserRouter, createRoutesFromElements, Route, RouterProvider } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { DefaultLayout, AuthLayout } from '@shared/components'
import { UserProvider } from '@shared/contexts'
import { IndexGate, GuestRoute, ProtectedRoute } from '@shared/components/-routing/RouteGuards'
import { JobsLayout, JobsHint, JobDetail, JobCreate } from '@pages/jobs'
import ActorSearch from '@pages/actors'
import Profile from '@pages/profile'
import Feed from '@pages/feed'
import Post from '@pages/posts'
import Settings from '@pages/settings'
import Login from '@pages/login'
import Register from '@pages/register'

const TestRoute = import.meta.env.DEV
  ? lazy(() => import('./pages/test'))
  : null

const router = createBrowserRouter(
  createRoutesFromElements(
    <>
      <Route path='/' element={<DefaultLayout />}>
        <Route index element={<IndexGate />} />

        <Route path='jobs' element={<JobsLayout />}>
          <Route index element={<JobsHint />} />
          <Route path=':slug' element={<JobDetail />} />
        </Route>

        <Route path='users'>
          <Route index element={<ActorSearch type='user' />} />
          <Route path=':username' element={<Profile actorType='user' />} />
          <Route element={<ProtectedRoute />}>
            <Route path=':username/settings' element={<Settings />} />
          </Route>
        </Route>

        <Route path='companies'>
          <Route index element={<ActorSearch type='org' />} />
          <Route path=':slug' element={<Profile actorType='org' />} />
          <Route element={<ProtectedRoute />}>
            <Route path=':slug/settings' element={<Settings />} />
          </Route>
        </Route>

        <Route path='posts/:postId' element={<Post />} />

        {/* `jobs/new` is a flat static route so it wins over `jobs/:slug`. */}
        <Route element={<ProtectedRoute />}>
          <Route path='feed' element={<Feed />} />
          <Route path='jobs/new' element={<JobCreate />} />
        </Route>

        {TestRoute && (
          <Route path='/test' element={<Suspense fallback={<div>Loading...</div>}><TestRoute /></Suspense>} />
        )}
      </Route>

      <Route element={<AuthLayout />}>
        <Route element={<GuestRoute />}>
          <Route path='login' element={<Login />} />
          <Route path='register' element={<Register />} />
        </Route>
      </Route>
    </>
  )
)

const App = () => (
  <UserProvider>
    <RouterProvider router={router} />
  </UserProvider>
)

export default App
