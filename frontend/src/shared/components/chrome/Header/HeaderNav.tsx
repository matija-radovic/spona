import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { NavItem } from './MobileNav'

interface HeaderNavProps {
  items: readonly NavItem[]
}

const HeaderNav = ({ items }: HeaderNavProps) => {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  return (
    <ul className="header-nav">
      {items.map(({ to, labelKey, isActive }) => {
        const active = isActive(pathname)
        return (
          <li key={to}>
            <Link
              to={to}
              aria-current={active ? 'page' : undefined}
              className={`header-link${active ? ' header-link-active' : ''}`}
            >
              {t(labelKey)}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

export default HeaderNav
