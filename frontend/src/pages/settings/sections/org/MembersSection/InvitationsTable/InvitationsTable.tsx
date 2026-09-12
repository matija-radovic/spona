import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconX } from '@tabler/icons-react'
import { ActorAvatar, ActorAvatarSize } from '@shared/components'
import useFormat from '@shared/hooks/useFormat'
import type { InvitationDto } from '@utils/api/organizations'
import { actorDisplayName } from '@utils/actorDisplayName'
import { RoleChip } from '../../../../components/RoleChip'

interface InvitationsTableProps {
  invitations: readonly InvitationDto[]
  busyId: string | null
  onCancel: (invitation: InvitationDto) => void
  onDismiss: (invitation: InvitationDto) => void
}

const InvitationsTable = ({
  invitations,
  busyId,
  onCancel,
  onDismiss,
}: InvitationsTableProps) => {
  const { t } = useTranslation()
  const format = useFormat()

  return (
    <table className="invitations-table">
      <thead>
        <tr>
          <th scope="col">{t('settings.invitations.invitee')}</th>
          <th scope="col">{t('settings.invitations.sentAt')}</th>
          <th scope="col">{t('settings.invitations.invitedAs')}</th>
          <th scope="col">{t('settings.invitations.statusLabel')}</th>
          <th scope="col">
            <span className="members-table-sr">{t('settings.invitations.cancel')}</span>
          </th>
        </tr>
      </thead>

      <tbody>
        {invitations.map((invitation) => {
          const declined = invitation.status === 'DECLINED'
          return (
            <tr key={invitation.id} className="members-row">
              <td className="members-cell-identity" data-label={t('settings.invitations.invitee')}>
                {invitation.invitee ? (
                  <div className="members-identity">
                    <ActorAvatar
                      avatarUrl={invitation.invitee.avatarUrl}
                      actorType="user"
                      size={ActorAvatarSize.SM}
                      alt={actorDisplayName(invitation.invitee.fullName, invitation.invitee.username)}
                    />
                    <span className="members-identity-text">
                      <span className="members-identity-name">
                        {actorDisplayName(invitation.invitee.fullName, invitation.invitee.username)}
                      </span>
                      <Link
                        className="members-identity-handle"
                        to={`/users/${invitation.invitee.username}`}
                      >
                        @{invitation.invitee.username}
                      </Link>
                    </span>
                  </div>
                ) : (
                  <span className="members-identity-text">
                    <span className="members-identity-name">{invitation.email}</span>
                    <span className="members-identity-handle is-muted">
                      {t('settings.invitations.byEmail')}
                    </span>
                  </span>
                )}
              </td>

              <td data-label={t('settings.invitations.sentAt')}>
                {format.date(invitation.createdAt)}
              </td>

              <td data-label={t('settings.invitations.invitedAs')}>
                <RoleChip role={invitation.role} />
              </td>

              <td data-label={t('settings.invitations.statusLabel')} className="invitations-status">
                {declined
                  ? `${t('settings.invitations.declined')}${
                      invitation.resolvedAt ? ` · ${format.date(invitation.resolvedAt)}` : ''
                    }`
                  : t('settings.invitations.waiting')}
              </td>

              <td className="members-action">
                <button
                  type="button"
                  className="settings-icon-button"
                  disabled={busyId === invitation.id}
                  onClick={() => (declined ? onDismiss(invitation) : onCancel(invitation))}
                  aria-label={
                    declined ? t('settings.invitations.dismiss') : t('settings.invitations.cancel')
                  }
                >
                  <IconX aria-hidden />
                </button>
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default InvitationsTable
