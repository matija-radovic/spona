import { useRef } from 'react'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import useHorizontalWheelScroll from '@shared/hooks/useHorizontalWheelScroll'
import type { SettingsSection } from '../../lib/sections'

interface SettingsSidebarProps {
  sections: readonly SettingsSection[]
}

const SettingsSidebar = ({ sections }: SettingsSidebarProps) => {
  const { t } = useTranslation()
  const laneRef = useRef<HTMLDivElement>(null)
  useHorizontalWheelScroll(laneRef)

  return (
    <nav className="settings-sidebar" aria-label={t('nav.settings')}>
      <div className="settings-sidebar-lane" ref={laneRef}>
        {sections.map(({ path, labelKey, icon: SectionIcon }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) => `settings-sidebar-link${isActive ? ' is-active' : ''}`}
          >
            <SectionIcon aria-hidden />
            {t(labelKey)}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

export default SettingsSidebar
