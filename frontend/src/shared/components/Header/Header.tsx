import './Header.scss'
import { useEffect, useRef } from 'react'
import { useUser } from '@shared/contexts'
import HeaderGuest from './HeaderGuest'
import HeaderAuth from './HeaderAuth'

const Header = () => {
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

  return (
    <header className="header" ref={headerRef}>
      {isAuthenticated ? <HeaderAuth /> : <HeaderGuest />}
    </header>
  )
}

export default Header
