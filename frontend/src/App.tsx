import './App.scss'
import { createBrowserRouter, createRoutesFromElements, Navigate, Route, RouterProvider } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { DefaultLayout, AuthLayout } from '@shared/components'
import { UserProvider } from '@shared/contexts'
import { IndexGate, GuestRoute, ProtectedRoute } from '@shared/components/-routing/RouteGuards'
import AuthBoundary from '@shared/components/-routing/AuthBoundary'
import { JobsLayout, JobsHint, JobDetail, JobCreate } from '@pages/jobs'
import ActorSearch from '@pages/actors'
import Profile from '@pages/profile'
import Feed from '@pages/feed'
import Post from '@pages/posts'
import {
  UserSettings,
  OrgSettings,
  UserProfileSection,
  OrganizationsSection,
  AccountSection,
  OrgProfileSection,
  MembersSection,
  ManagementSection,
  PrivacySection,
} from '@pages/settings'
import Invitations from '@pages/invitations'
import Login from '@pages/login'
import Register from '@pages/register'
import VerifyEmail from '@pages/verify'
import { RequestPasswordReset, ConfirmPasswordReset } from '@pages/reset'
import CreateOrganization from '@pages/organizations'

const TestRoute = import.meta.env.DEV
  ? lazy(() => import('./pages/test'))
  : null

const router = createBrowserRouter(
  createRoutesFromElements(
    <>
      <Route element={<AuthBoundary />}>
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
              <Route path=':username/settings' element={<UserSettings />}>
                <Route index element={<Navigate to='profile' replace />} />
                <Route path='profile' element={<UserProfileSection />} />
                <Route path='privacy' element={<PrivacySection />} />
                <Route path='organizations' element={<OrganizationsSection />} />
                <Route path='account' element={<AccountSection />} />
              </Route>
            </Route>
          </Route>

          <Route path='companies'>
            <Route index element={<ActorSearch type='org' />} />
            <Route path=':slug' element={<Profile actorType='org' />} />
            <Route element={<ProtectedRoute />}>
              <Route path='new' element={<CreateOrganization />} />
              <Route path=':slug/settings' element={<OrgSettings />}>
                <Route index element={<Navigate to='profile' replace />} />
                <Route path='profile' element={<OrgProfileSection />} />
                <Route path='privacy' element={<PrivacySection />} />
                <Route path='members' element={<MembersSection />} />
                <Route path='management' element={<ManagementSection />} />
              </Route>
            </Route>
          </Route>

          <Route path='posts/:postId' element={<Post />} />

          <Route element={<ProtectedRoute />}>
            <Route path='feed' element={<Feed />} />
            <Route path='jobs/new' element={<JobCreate />} />
            <Route path='invitations' element={<Invitations />} />
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
      </Route>

      <Route element={<AuthLayout />}>
        <Route path='verify/:token' element={<VerifyEmail />} />
        <Route path='reset' element={<RequestPasswordReset />} />
        <Route path='reset/:token' element={<ConfirmPasswordReset />} />
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
