import { Navigate } from 'react-router-dom'
import { useUser } from '@shared/contexts'

const Invitations = () => {
  const { user } = useUser()
  if (!user) return null

  return <Navigate to={`/users/${user.username}/settings/organizations`} replace />
}

export default Invitations
