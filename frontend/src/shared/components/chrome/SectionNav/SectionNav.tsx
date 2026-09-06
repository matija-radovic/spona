import './SectionNav.scss'
import { useRef } from 'react'
import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import useHorizontalWheelScroll from '@shared/hooks/useHorizontalWheelScroll'
import type { SectionItem } from './sectionItem'

interface SectionNavProps {
  sections: readonly SectionItem[]
  /** Accessible name of the lane — the surface it navigates. */
  label: string
}

const SectionNav = ({ sections, label }: SectionNavProps) => {
  const { t } = useTranslation()
  const laneRef = useRef<HTMLDivElement>(null)
  useHorizontalWheelScroll(laneRef)

  return (
    <nav className="section-nav" aria-label={label}>
      <div className="section-nav-lane" ref={laneRef}>
        {sections.map(({ path, labelKey, icon: SectionIcon }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) => `section-nav-link${isActive ? ' is-active' : ''}`}
          >
            <SectionIcon aria-hidden />
            {t(labelKey)}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}

export default SectionNav
