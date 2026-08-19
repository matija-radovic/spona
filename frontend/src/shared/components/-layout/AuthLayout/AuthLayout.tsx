import './AuthLayout.scss'
import { useEffect, useRef } from 'react'
import { Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Footer } from '@shared/components/Footer'
import { markInitialLoadDone } from '@shared/hooks/useLastSection'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'
import { afterAuthPath } from '@utils/afterAuth'

const AuthLayout = () => {
  const { t } = useTranslation()
  const mainRef = useRef<HTMLElement>(null)
  useGhostScrollbar(mainRef)

  useEffect(() => {
    markInitialLoadDone()
  }, [])

  // The invitation deep link gets its own line: it matters that they sign in with the
  // account the invitation was addressed to, or their invites page shows nothing.
  const target = afterAuthPath()
  const hintKey =
    target === '/invitations' ? 'settings.organizations.authHint' : 'auth.continueHint'

  return (
    <div className="auth-layout">
      <main ref={mainRef} className="auth-layout-main scroll-container">
        <div className="auth-layout-content">
          {target && <p className="auth-layout-continue-hint">{t(hintKey)}</p>}
          <Outlet />
        </div>
        <Footer />
      </main>
    </div>
  )
}

export default AuthLayout
