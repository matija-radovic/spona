import './InvitationModal.scss'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize, Button, ButtonVariant } from '@shared/components'
import { actorPath } from '@utils/actorPath'
import type { ActorRefDto } from '@utils/api/profiles'
import type { OrganizationRole } from '@utils/api/organizations'
import { SettingsModal } from '../../../../components/SettingsModal'
import { RoleChip } from '../../../../components/RoleChip'
import { InviteModalState } from './inviteModalState'

interface InvitationModalProps {
  isOpen: boolean
  state: InviteModalState
  organization: ActorRefDto | null
  role: OrganizationRole | null
  invitedBy: ActorRefDto | null
  busy?: boolean
  error?: string | null
  onAccept?: () => void
  onDecline?: () => void
  /** Only the wrong-account branch offers this */
  onLogout?: () => void
  /** Kasnije/U redu/Zatvori/Esc/backdrop */
  onClose: () => void
}
const InvitationModal = ({
  isOpen,
  state,
  organization,
  role,
  invitedBy,
  busy = false,
  error,
  onAccept,
  onDecline,
  onLogout,
  onClose,
}: InvitationModalProps) => {
  const { t } = useTranslation()

  const isWaiting = state === InviteModalState.WAITING
  const isWrongAccount = state === InviteModalState.WRONG_ACCOUNT

  const footer = isWaiting ? (
    <>
      <Button variant={ButtonVariant.GHOST} onClick={onClose} disabled={busy}>
        {t('settings.invite.later')}
      </Button>
      <Button variant={ButtonVariant.TERTIARY} onClick={onDecline} disabled={busy}>
        {t('settings.invite.decline')}
      </Button>
      <Button variant={ButtonVariant.PRIMARY} onClick={onAccept} disabled={busy}>
        {busy ? t('common.loading') : t('settings.invite.accept')}
      </Button>
    </>
  ) : isWrongAccount ? (
    <>
      <Button variant={ButtonVariant.GHOST} onClick={onClose}>
        {t('common.close')}
      </Button>
      <Button variant={ButtonVariant.TERTIARY} onClick={onLogout}>
        {t('settings.invite.logout')}
      </Button>
    </>
  ) : (
    <Button variant={ButtonVariant.PRIMARY} onClick={onClose}>
      {t('settings.invite.ok')}
    </Button>
  )

  return (
    <SettingsModal
      isOpen={isOpen}
      onClose={onClose}
      title={t('settings.invite.title')}
      footer={footer}
    >
      {organization && (
        <div className="invitation-modal-org">
          <ActorAvatar
            avatarUrl={organization.avatarUrl}
            actorType="org"
            size={ActorAvatarSize.MD}
            alt={organization.name}
          />
          <div className="invitation-modal-identity">
            <Link className="invitation-modal-name" to={actorPath(organization)}>
              {organization.name}
            </Link>
            {invitedBy && (
              <span className="invitation-modal-inviter">
                {t('settings.organizations.invitedYou', { name: invitedBy.name })}
              </span>
            )}
          </div>
        </div>
      )}

      {role && (
        <p className="invitation-modal-role">
          <span className="invitation-modal-role-label">{t('settings.invite.role')}</span>
          <RoleChip role={role} />
        </p>
      )}

      {!isWaiting && <p>{t(`settings.invite.${state}`)}</p>}

      {error && <p className="settings-modal-error">{error}</p>}
    </SettingsModal>
  )
}

export default InvitationModal
