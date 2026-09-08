import './AuthLayout.scss'
import { useEffect, useRef, type ReactNode } from 'react'
import { Outlet } from 'react-router-dom'
import { Footer } from '@shared/components/chrome/Footer'
import { markInitialLoadDone } from '@shared/hooks/useLastSection'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'

interface AuthLayoutProps {
  children?: ReactNode
}

const AuthLayout = ({ children }: AuthLayoutProps) => {
  const mainRef = useRef<HTMLElement>(null)
  useGhostScrollbar(mainRef)

  useEffect(() => {
    markInitialLoadDone()
  }, [])

  return (
    <div className="auth-layout">
      <main ref={mainRef} className="auth-layout-main scroll-container">
        <div className="auth-layout-content">{children ?? <Outlet />}</div>
        <Footer />
      </main>
    </div>
  )
}

export default AuthLayout
