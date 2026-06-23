import './Header.scss'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Logo } from '@shared/components/Logo'
import { Button, ButtonVariant } from '@shared/components/Button'
import { useUser } from '@shared/contexts'

interface NavItem {
  to: string
  labelKey: string
}

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', labelKey: 'nav.home' },
  { to: '/jobs', labelKey: 'nav.jobs' },
  { to: '/companies', labelKey: 'nav.companies' },
]

const Header = () => {
  const user = useUser();
  const { t } = useTranslation()

  return (
    <header className="header">
      {!user.isAuthenticated ? (
        <div className="header-inner container">
          <Logo asLink/>

          <nav className="header-right" aria-label={t('nav.home')}>
            <ul className="header-nav">
              {NAV_ITEMS.map(({ to, labelKey }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    end={to === '/'}
                    className={({ isActive }) =>
                      `header-link${isActive ? ' header-link-active' : ''}`
                    }
                  >
                    {t(labelKey)}
                  </NavLink>
                </li>
              ))}
            </ul>

            <div className="header-auth">
              <Button to="/login" variant={ButtonVariant.SECONDARY}>
                {t('nav.signIn')}
              </Button>
              <Button to="/register" variant={ButtonVariant.PRIMARY}>
                {t('nav.registerNow')}
              </Button>
            </div>
          </nav>
        </div>
      ) : (
        <></>
      )}
    </header>
  )
}

export default Header
