import './ActorByline.scss'
import { Link } from 'react-router-dom'
import { ActorAvatar, ActorAvatarSize } from '@shared/components/actor/ActorAvatar'
import useFormat from '@shared/hooks/useFormat'
import { actorPath } from '@utils/actorPath'
import { actorDisplayName } from '@utils/actorDisplayName'
import type { ActorRefDto } from '@utils/api/profiles'

interface ActorBylineProps {
  actor: ActorRefDto
  /** ISO timestamp of the thing being attributed. */
  createdAt: string
  size?: ActorAvatarSize
  className?: string
}

/**
 * Avatar + name + `@handle` + date for content like components
 */
const ActorByline = ({ actor, createdAt, size = ActorAvatarSize.SM, className }: ActorBylineProps) => {
  const format = useFormat()

  return (
    <Link
      to={actorPath(actor)}
      className={['actor-byline', className].filter(Boolean).join(' ')}
      onClick={(event) => event.stopPropagation()}
    >
      <ActorAvatar avatarUrl={actor.avatarUrl} actorType={actor.actorType} size={size} />
      <span className="actor-byline-identity">
        <span className="actor-byline-name">{actorDisplayName(actor.name, actor.handle)}</span>
        <span className="actor-byline-sub" title={format.date(createdAt, 'dateTime')}>
          {actor.actorType === 'user'
            ? `@${actor.handle} • ${format.date(createdAt, 'short')}`
            : format.date(createdAt, 'short')}
        </span>
      </span>
    </Link>
  )
}

export default ActorByline
