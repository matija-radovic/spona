import './AuthLayout.scss'
import { useEffect, useRef } from 'react'
import { Outlet } from 'react-router-dom'
import { Footer } from '@shared/components/chrome/Footer'
import { markInitialLoadDone } from '@shared/hooks/useLastSection'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'

const AuthLayout = () => {
  const mainRef = useRef<HTMLElement>(null)
  useGhostScrollbar(mainRef)

  useEffect(() => {
    markInitialLoadDone()
  }, [])

  return (
    <div className="auth-layout">
      <main ref={mainRef} className="auth-layout-main scroll-container">
        <div className="auth-layout-content">
          <Outlet />
        </div>
        <Footer />
      </main>
    </div>
  )
}

export default AuthLayout
