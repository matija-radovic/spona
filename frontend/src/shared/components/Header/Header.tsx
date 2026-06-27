import './Header.scss'
import { useUser } from '@shared/contexts'
import HeaderGuest from './HeaderGuest'
import HeaderAuth from './HeaderAuth'

const Header = () => {
  const { isAuthenticated } = useUser()

  return (
    <header className="header">
      {isAuthenticated ? <HeaderAuth /> : <HeaderGuest />}
    </header>
  )
}

export default Header
