import './ActorAvatar.scss'
import { IconUser, IconBuilding } from '@tabler/icons-react'

export const ActorAvatarSize = Object.freeze({
  SM: 'sm',
  MD: 'md',
  XL: 'xl',
} as const)

export type ActorAvatarSize = (typeof ActorAvatarSize)[keyof typeof ActorAvatarSize]

const ICON_SIZE: Record<ActorAvatarSize, number> = {
  sm: 20,
  md: 30,
  xl: 52,
}

interface ActorAvatarProps {
  avatarUrl: string | null
  actorType: 'user' | 'org'
  size?: ActorAvatarSize
  /** Decorative by default (empty alt); pass a name where the avatar stands alone. */
  alt?: string
}

/**
 * The circular actor avatar - a solid-border, non-interactive mirror of
 * AvatarPicker's circle. Falls back to a centered icon (building for orgs, user
 * otherwise) when there is no image. Reused by the profile head, post cards,
 * search rows and the header avatar button via the size variant.
 */
const ActorAvatar = ({ avatarUrl, actorType, size = ActorAvatarSize.MD, alt = '' }: ActorAvatarProps) => {
  const Fallback = actorType === 'org' ? IconBuilding : IconUser

  return (
    <span className={`actor-avatar actor-avatar-${size}`}>
      {avatarUrl ? (
        <img src={avatarUrl} alt={alt} className="actor-avatar-image" />
      ) : (
        <Fallback className="actor-avatar-placeholder" size={ICON_SIZE[size]} aria-hidden />
      )}
    </span>
  )
}

export default ActorAvatar
