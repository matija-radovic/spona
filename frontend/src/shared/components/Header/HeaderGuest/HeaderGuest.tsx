import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconHome2, IconBriefcase2, IconBuildingFactory2 } from '@tabler/icons-react'
import { Logo } from '@shared/components/Logo'
import { Button, ButtonVariant } from '@shared/components/Button'
import MobileNav, { type NavItem } from '../MobileNav'

const NAV_ITEMS: readonly NavItem[] = [
  { to: '/', labelKey: 'nav.home', icon: IconHome2 },
  { to: '/jobs', labelKey: 'nav.jobs', icon: IconBriefcase2 },
  { to: '/companies', labelKey: 'nav.companies', icon: IconBuildingFactory2 },
]

const HeaderGuest = () => {
  const { t } = useTranslation()

  return (
    <div className="header-inner container">
      <Logo asLink />

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

      <MobileNav items={NAV_ITEMS} isAuthenticated={false} />
    </div>
  )
}

export default HeaderGuest
