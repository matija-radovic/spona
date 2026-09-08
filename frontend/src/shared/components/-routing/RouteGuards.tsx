import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useUser } from '@shared/contexts'
import { getLastSection, isInitialLanding } from '@shared/hooks/useLastSection'
import Main from '@pages/main'
import { afterAuthPath, forgetAfterAuth, rememberAfterAuth } from '@utils/afterAuth'
import AuthRecoveryBoundary from './AuthRecoveryBoundary'

/**
 * The remembered-tab redirect only fires on the initial page load; once in a
 * session the visitor can always navigate back to the home page.
 */
export const IndexGate = () => {
  const { isAuthenticated } = useUser()

  if (isAuthenticated) {
    return <Navigate to="/feed" replace />
  }

  if (isInitialLanding()) {
    const last = getLastSection()
    if (last) {
      return <Navigate to={last} replace />
    }
  }

  return <Main />
}

/**
 * Routes that require an authenticated user. A visitor without one is refused
 * here and remembered; a page below that finds no user despite this guard has
 * broken an invariant, and the boundary answers that instead.
 */
export const ProtectedRoute = () => {
  const { isAuthenticated } = useUser()
  const location = useLocation()

  if (isAuthenticated) {
    return (
      <AuthRecoveryBoundary>
        <Outlet />
      </AuthRecoveryBoundary>
    )
  }

  rememberAfterAuth(`${location.pathname}${location.search}`)
  return <Navigate to="/login" replace />
}

/** Routes only for logged-out visitors (login, register). */
export const GuestRoute = () => {
  const { isAuthenticated } = useUser()
  const target = afterAuthPath()

  useEffect(() => {
    if (isAuthenticated) forgetAfterAuth()
  }, [isAuthenticated])

  if (!isAuthenticated) return <Outlet />

  return <Navigate to={target ?? '/feed'} replace />
}
