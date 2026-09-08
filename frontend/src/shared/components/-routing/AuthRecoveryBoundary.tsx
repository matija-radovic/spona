import { Component, type ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { MissingUserError } from '@shared/contexts'
import { clearSessionData } from '@utils/session'

interface AuthRecoveryBoundaryProps {
  children: ReactNode
}

interface AuthRecoveryBoundaryState {
  error: unknown
}

/**
 * The one place a `MissingUserError` is answered, sits inside `ProtectedRoute`,
 * deletes the current session and redirects to login page
 */
class AuthRecoveryBoundary extends Component<AuthRecoveryBoundaryProps, AuthRecoveryBoundaryState> {
  state: AuthRecoveryBoundaryState = { error: null }

  static getDerivedStateFromError(error: unknown): AuthRecoveryBoundaryState {
    return { error }
  }

  // Runs before the fallback's own effects, so the session is gone by the time the redirect fires.
  componentDidCatch(error: unknown) {
    if (error instanceof MissingUserError) clearSessionData(null)
  }

  render() {
    const { error } = this.state
    if (error === null) return this.props.children
    if (!(error instanceof MissingUserError)) throw error
    return <Navigate to="/login" replace />
  }
}

export default AuthRecoveryBoundary
