import './PostAsSelect.scss'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize } from '@shared/components/actor/ActorAvatar'
import { Dropdown, type DropdownOption } from '@shared/components/form/Dropdown'
import { useAuthenticatedUser } from '@shared/contexts'
import { MQ } from '@shared/css/mediaqueries'
import useMediaQuery from '@shared/hooks/useMediaQuery'
import type { MyOrganizationDto } from '@utils/api/memberships'
import { actorDisplayName } from '@utils/actorDisplayName'
import type { PostAsValue } from './postAsValue'

const MYSELF = ''

const MAX_LABEL_CHARS = 22
const MAX_LABEL_CHARS_PHONE = 14

interface PostAsSelectProps {
  /** Organizations the viewer may publish to ON THIS SURFACE. */
  organizations: readonly MyOrganizationDto[]
  value: PostAsValue
  onChange: (next: PostAsValue) => void
  disabled?: boolean
  className?: string
}

const PostAsSelect = ({ organizations, value, onChange, disabled, className }: PostAsSelectProps) => {
  const { t } = useTranslation()
  const user = useAuthenticatedUser('PostAsSelect')
  const isPhone = useMediaQuery(MQ.phone)

  const options: DropdownOption[] = [
    {
      value: MYSELF,
      label: actorDisplayName(user.fullName, user.username),
      leading: (
        <ActorAvatar avatarUrl={user.avatarUrl || null} actorType="user" size={ActorAvatarSize.XS} />
      ),
    },
    ...organizations.map((row) => ({
      value: row.organization.id,
      label: row.organization.name,
      leading: (
        <ActorAvatar
          avatarUrl={row.organization.avatarUrl}
          actorType={row.organization.actorType}
          size={ActorAvatarSize.XS}
        />
      ),
    })),
  ]

  return (
    <Dropdown
      label={t('post.postedAs')}
      options={options}
      value={value ?? MYSELF}
      onChange={(next) => onChange(next === MYSELF ? null : (next as string))}
      placeholder={actorDisplayName(user.fullName, user.username)}
      maxLabelChars={isPhone ? MAX_LABEL_CHARS_PHONE : MAX_LABEL_CHARS}
      disabled={disabled}
      className={['post-as-select', className].filter(Boolean).join(' ')}
    />
  )
}

export default PostAsSelect
