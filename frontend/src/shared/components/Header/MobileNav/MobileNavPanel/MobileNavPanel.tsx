import './MobileNavPanel.scss'
import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'motion/react'
import { NavLink, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/Button'
import type { NavItem } from '../MobileNav'

const backdrop = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
  transition: { duration: 0.22, ease: 'easeOut' },
} as const

const panel = {
  initial: { x: '100%' },
  animate: { x: 0 },
  exit: { x: '100%' },
  transition: { type: 'spring', stiffness: 320, damping: 34 },
} as const

interface MobileNavPanelProps {
  isOpen: boolean
  onClose: () => void
  items: readonly NavItem[]
  isAuthenticated: boolean
}

const MobileNavPanel = ({ isOpen, onClose, items, isAuthenticated }: MobileNavPanelProps) => {
  const { t } = useTranslation()
  const location = useLocation()

  // Auto-dismiss when navigating to a new route (e.g. tapping a panel link).
  useEffect(() => {
    onClose()
    // Intentionally keyed on the path only - including onClose would close the
    // panel on every parent re-render, undoing the open toggle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname])

  // Esc-to-close and body scroll lock, active only while the panel is open.
  useEffect(() => {
    if (!isOpen) return

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }

    window.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'

    return () => {
      window.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
    }
  }, [isOpen, onClose])

  return createPortal(
    <AnimatePresence>
      {isOpen && (
        <motion.div className="mobile-nav">
          <motion.div className="mobile-nav-backdrop" onClick={onClose} {...backdrop} />

          <motion.aside
            id="mobile-nav-panel"
            className="mobile-nav-panel"
            role="dialog"
            aria-modal="true"
            aria-label={t('nav.menu')}
            onClick={(e) => e.stopPropagation()}
            {...panel}
          >
            <nav className="mobile-nav-links" aria-label={t('nav.menu')}>
              {items.map(({ to, labelKey, icon: Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  end={to === '/'}
                  className={({ isActive }) =>
                    `mobile-nav-link${isActive ? ' mobile-nav-link-active' : ''}`
                  }
                >
                  <Icon className="mobile-nav-link-icon" size={22} stroke={1.75} aria-hidden />
                  {t(labelKey)}
                </NavLink>
              ))}
            </nav>

            {!isAuthenticated && (
              <div className="mobile-nav-auth">
                <Button to="/login" variant={ButtonVariant.SECONDARY} fullWidth>
                  {t('nav.signIn')}
                </Button>
                <Button to="/register" variant={ButtonVariant.PRIMARY} fullWidth>
                  {t('nav.registerNow')}
                </Button>
              </div>
            )}
          </motion.aside>
        </motion.div>
      )}
    </AnimatePresence>,
    document.body,
  )
}

export default MobileNavPanel
