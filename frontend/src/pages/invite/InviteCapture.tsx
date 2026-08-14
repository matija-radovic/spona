import './InviteCapture.scss'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useUser } from '@shared/contexts'
import { resolveInvitationToken, type InvitationPreviewDto } from '@utils/api/memberships'
import { stashPendingInvite } from '@utils/pendingInvite'
import { InvitationModal, InviteModalState, stateFromPreview } from '@pages/settings'

/** `/i/:token` is a capture route, not a page: resolve the token, then route or open a modal. */
const InviteCapture = () => {
  const { t } = useTranslation()
  const { token } = useParams()
  const navigate = useNavigate()
  const { user, isAuthenticated, logout } = useUser()

  const [preview, setPreview] = useState<InvitationPreviewDto | null>(null)
  const [state, setState] = useState<InviteModalState | null>(null)

  useEffect(() => {
    if (!token) return
    let alive = true

    resolveInvitationToken(token)
      .then((resolved) => {
        if (!alive) return

        // Guest - park the token for the round trip through registration.
        if (!isAuthenticated || !user) {
          stashPendingInvite({ token, orgName: resolved.organization.name })
          navigate('/register', { replace: true })
          return
        }

        const next = stateFromPreview(resolved)
        if (next === InviteModalState.WAITING) {
          navigate(`/users/${user.username}/settings/organizations?invite=${resolved.id}`, {
            replace: true,
          })
          return
        }

        setPreview(resolved)
        setState(next)
      })
      .catch(() => {
        if (!alive) return
        setPreview(null)
        setState(InviteModalState.NOT_FOUND)
      })

    return () => {
      alive = false
    }
  }, [token, isAuthenticated, user, navigate])

  const onClose = () => {
    navigate(preview ? `/companies/${preview.organization.handle}` : '/', { replace: true })
  }

  return (
    <div className="invite-capture container">
      <p className="invite-capture-status">{t('common.loading')}</p>

      <InvitationModal
        isOpen={state !== null}
        state={state ?? InviteModalState.NOT_FOUND}
        organization={preview?.organization ?? null}
        role={preview?.role ?? null}
        invitedBy={preview?.invitedBy ?? null}
        onLogout={() => {
          logout()
          navigate('/login', { replace: true })
        }}
        onClose={onClose}
      />
    </div>
  )
}

export default InviteCapture
