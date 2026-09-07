import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  ActorAvatar,
  ActorAvatarSize,
  Button,
  ButtonVariant,
  Dropdown,
  type DropdownOption,
} from '@shared/components'
import useFormat from '@shared/hooks/useFormat'
import type { OrganizationRole, OrgMemberDto } from '@utils/api/organizations'
import { actorDisplayName } from '@utils/actorDisplayName'
import { RoleChip, RoleChipSize } from '../../../../components/RoleChip'
import { assignableRoles, canManageMember } from '../../../../lib/permissions'

interface MembersTableProps {
  members: readonly OrgMemberDto[]
  viewerRole: OrganizationRole
  viewerUsername: string | undefined
  busyUserId: string | null
  onRoleChange: (member: OrgMemberDto, role: OrganizationRole) => void
  onRemove: (member: OrgMemberDto) => void
}

const MembersTable = ({
  members,
  viewerRole,
  viewerUsername,
  busyUserId,
  onRoleChange,
  onRemove,
}: MembersTableProps) => {
  const { t } = useTranslation()
  const format = useFormat()

  const roleOptions: DropdownOption[] = assignableRoles(viewerRole).map((role) => ({
    value: role,
    label: t(`settings.roles.${role}`),
  }))

  return (
    <table className="members-table">
      <thead>
        <tr>
          <th scope="col">{t('settings.members.member')}</th>
          <th scope="col">{t('settings.members.joined')}</th>
          <th scope="col">{t('settings.members.role')}</th>
          <th scope="col">
            <span className="members-table-sr">{t('settings.members.remove')}</span>
          </th>
        </tr>
      </thead>

      <tbody>
        {members.map((member) => {
          const manageable = canManageMember(viewerRole, member, viewerUsername)
          return (
            <tr key={member.userId} className="members-row">
              <td className="members-cell-identity" data-label={t('settings.members.member')}>
                <div className="members-identity">
                  <ActorAvatar
                    avatarUrl={member.avatarUrl}
                    actorType="user"
                    size={ActorAvatarSize.SM}
                    alt={actorDisplayName(member.fullName, member.username)}
                  />
                  <span className="members-identity-text">
                    <span className="members-identity-name">
                      {actorDisplayName(member.fullName, member.username)}
                    </span>
                    <Link className="members-identity-handle" to={`/users/${member.username}`}>
                      @{member.username}
                    </Link>
                  </span>
                </div>
              </td>

              <td data-label={t('settings.members.joined')}>{format.date(member.assignedAt)}</td>

              <td data-label={t('settings.members.role')}>
                {manageable ? (
                  <Dropdown
                    className="members-role-dropdown"
                    label={t('settings.members.role')}
                    options={roleOptions}
                    value={member.role}
                    disabled={busyUserId === member.userId}
                    onChange={(next) => {
                      if (typeof next === 'string') onRoleChange(member, next as OrganizationRole)
                    }}
                  />
                ) : (
                  <RoleChip role={member.role} size={RoleChipSize.MD} />
                )}
              </td>

              <td className="members-action">
                {manageable && (
                  <Button
                    variant={ButtonVariant.DANGER_OUTLINE}
                    onClick={() => onRemove(member)}
                    disabled={busyUserId === member.userId}
                  >
                    {t('settings.members.remove')}
                  </Button>
                )}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export default MembersTable
