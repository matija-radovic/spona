import { NavLink, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconUserCircle, IconSettings, IconLogout } from '@tabler/icons-react'
import { useUser } from '@shared/contexts'

interface MobileAuthActionsProps {
  onClose: () => void
}

const MobileAuthActions = ({ onClose }: MobileAuthActionsProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, logout } = useUser()

  const onLogout = () => {
    onClose()
    logout()
    navigate('/')
  }

  return (
    <div className="mobile-nav-auth mobile-nav-account">
      <NavLink to={`/users/${user?.username ?? ''}`} className="mobile-nav-link">
        <IconUserCircle className="mobile-nav-link-icon" size={22} stroke={1.75} aria-hidden />
        {t('nav.myProfile')}
      </NavLink>
      <NavLink to={`/users/${user?.username ?? ''}/settings`} className="mobile-nav-link">
        <IconSettings className="mobile-nav-link-icon" size={22} stroke={1.75} aria-hidden />
        {t('nav.settings')}
      </NavLink>
      <button type="button" className="mobile-nav-link mobile-nav-logout" onClick={onLogout}>
        <IconLogout className="mobile-nav-link-icon" size={22} stroke={1.75} aria-hidden />
        {t('nav.logout')}
      </button>
    </div>
  )
}

export default MobileAuthActions
