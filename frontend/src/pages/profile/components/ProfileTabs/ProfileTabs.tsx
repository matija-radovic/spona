import './ProfileTabs.scss'
import { useRef } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react'
import useOnScreen from '@shared/hooks/useOnScreen'
import useHorizontalWheelScroll from '@shared/hooks/useHorizontalWheelScroll'
import type { ProfileTab, ProfileTabItem } from './profileTab'

const hint = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.15, ease: 'easeOut' },
} as const

interface ProfileTabsProps {
  tabs: ProfileTabItem[]
  active: ProfileTab
  onChange: (key: ProfileTab) => void
}

/**
 * A dumb, left-aligned tab strip. The active key lives in Profile (useState) -
 * no router involvement. The active tab is underlined in goldenrod.
 */
const ProfileTabs = ({ tabs, active, onChange }: ProfileTabsProps) => {
  const laneRef = useRef<HTMLDivElement>(null)
  const startRef = useRef<HTMLSpanElement>(null)
  const endRef = useRef<HTMLSpanElement>(null)

  const atStart = useOnScreen(startRef, { root: laneRef })
  const atEnd = useOnScreen(endRef, { root: laneRef })

  useHorizontalWheelScroll(laneRef)

  return (
    <div className="profile-tabs">
      <div className="profile-tabs-lane" ref={laneRef}>
        <div className="profile-tabs-track" role="tablist">
          <span ref={startRef} className="profile-tabs-edge profile-tabs-edge-start" aria-hidden />

          {tabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              role="tab"
              aria-selected={tab.key === active}
              className={`profile-tab${tab.key === active ? ' is-active' : ''}`}
              onClick={(e) => {
                e.currentTarget.scrollIntoView({
                  inline: 'nearest',
                  block: 'nearest',
                  behavior: 'smooth',
                })
                onChange(tab.key)
              }}
            >
              {tab.label}
            </button>
          ))}

          <span ref={endRef} className="profile-tabs-edge profile-tabs-edge-end" aria-hidden />
        </div>
      </div>

      <AnimatePresence>
        {!atStart && (
          <motion.span
            key="start"
            className="profile-tabs-hint profile-tabs-hint-start"
            aria-hidden
            {...hint}
          >
            <IconChevronLeft size={14} stroke={1.75} />
          </motion.span>
        )}

        {!atEnd && (
          <motion.span
            key="end"
            className="profile-tabs-hint profile-tabs-hint-end"
            aria-hidden
            {...hint}
          >
            <IconChevronRight size={14} stroke={1.75} />
          </motion.span>
        )}
      </AnimatePresence>
    </div>
  )
}

export default ProfileTabs
