import './AuthLayout.scss'
import { useEffect, useRef } from 'react'
import { Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Footer } from '@shared/components/Footer'
import { markInitialLoadDone } from '@shared/hooks/useLastSection'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'
import { peekPendingInvite } from '@utils/pendingInvite'

const AuthLayout = () => {
  const { t } = useTranslation()
  const mainRef = useRef<HTMLElement>(null)
  useGhostScrollbar(mainRef)

  useEffect(() => {
    markInitialLoadDone()
  }, [])

  const pendingInvite = peekPendingInvite()

  return (
    <div className="auth-layout">
      <main ref={mainRef} className="auth-layout-main scroll-container">
        <div className="auth-layout-content">
          {pendingInvite && (
            <p className="auth-layout-invite-hint">
              {t('settings.invite.authHint', { name: pendingInvite.orgName })}
            </p>
          )}
          <Outlet />
        </div>
        <Footer />
      </main>
    </div>
  )
}

export default AuthLayout
