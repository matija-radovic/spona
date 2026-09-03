import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { NavItem } from './MobileNav'

interface HeaderNavProps {
  items: readonly NavItem[]
}

const HeaderNav = ({ items }: HeaderNavProps) => {
  const { t } = useTranslation()
  return (
    <ul className="header-nav">
      {items.map(({ to, labelKey, end }) => (
        <li key={to}>
          <NavLink
            to={to}
            end={end}
            className={({ isActive }) => `header-link${isActive ? ' header-link-active' : ''}`}
          >
            {t(labelKey)}
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

export default HeaderNav
