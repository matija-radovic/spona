import { Outlet } from 'react-router-dom'
import { useUser } from '@shared/contexts'
import RouteLoader from './RouteLoader'

/**
 * Layout route for the auth-reactive part of the tree. It holds the routes back
 * behind a full-screen loader until the one-time session bootstrap settles, so
 * no header or page paints a guest state that then flips to signed-in.
 */
const AuthBoundary = () => {
  const { isBootstrapping } = useUser()
  if (isBootstrapping) return <RouteLoader />
  return <Outlet />
}

export default AuthBoundary
