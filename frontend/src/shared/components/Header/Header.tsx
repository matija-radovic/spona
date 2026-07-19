import './Header.scss'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useUser } from '@shared/contexts'
import { Logo } from '@shared/components/Logo'
import HeaderNav from './HeaderNav'
import HeaderGuestActions from './HeaderActions/HeaderGuestActions'
import HeaderAuthActions from './HeaderActions/HeaderAuthActions'
import MobileNav from './MobileNav'
import { GUEST_NAV_ITEMS, AUTH_NAV_ITEMS } from './navItems'

const Header = () => {
  const { t } = useTranslation()
  const { isAuthenticated } = useUser()
  const headerRef = useRef<HTMLElement>(null)

  // Publish the header's live height as `--header-height` so bounded-height
  // routes (e.g. /jobs) can size themselves to the viewport minus the header
  // without hard-coding a pixel value that breaks when the header wraps/resizes.
  useEffect(() => {
    const el = headerRef.current
    if (!el) return
    const publish = () =>
      document.documentElement.style.setProperty('--header-height', `${el.offsetHeight}px`)
    publish()
    const observer = new ResizeObserver(publish)
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const items = isAuthenticated ? AUTH_NAV_ITEMS : GUEST_NAV_ITEMS
  const Actions = isAuthenticated ? HeaderAuthActions : HeaderGuestActions

  return (
    <header className="header" ref={headerRef}>
      <div className="header-inner container">
        <Logo asLink />

        <nav className="header-right" aria-label={t('nav.menu')}>
          <HeaderNav items={items} />
          <Actions />
        </nav>

        <MobileNav items={items} isAuthenticated={isAuthenticated} />
      </div>
    </header>
  )
}

export default Header
