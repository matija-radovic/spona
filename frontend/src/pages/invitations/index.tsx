import { Navigate } from 'react-router-dom'
import { useAuthenticatedUser } from '@shared/contexts'

const Invitations = () => {
  const user = useAuthenticatedUser('Invitations')

  return <Navigate to={`/users/${user.username}/settings/organizations`} replace />
}

export default Invitations
