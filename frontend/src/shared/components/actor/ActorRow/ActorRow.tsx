import './ActorRow.scss'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize } from '@shared/components/actor/ActorAvatar'
import { FollowButton } from '@shared/components/actor/FollowButton'
import { useUser } from '@shared/contexts'
import { actorPath } from '@utils/actorPath'
import { actorDisplayName } from '@utils/actorDisplayName'
import { truncateText } from '@utils/truncateText'
import type { ActorRefDto } from '@utils/api/profiles'
import { ActorRowVariant } from './actorRowVariant'

/** max desc count before read more spawns*/
const DESCRIPTION_MAX_LENGTH = 160

interface ActorRowProps {
  actor: ActorRefDto
  /** The caller's own edge to this actor; the row owns the state from here. */
  followedByViewer?: boolean
  /** Drawn under the identity and truncated to a read more toggle when long. */
  description?: string | null
  variant?: ActorRowVariant
  /** Reports the row's own flip, for a caller with a number to move. */
  onFollowChange?: (next: boolean) => void
}
const ActorRow = ({
  actor,
  followedByViewer,
  description,
  variant = ActorRowVariant.BOXED,
  onFollowChange,
}: ActorRowProps) => {
  const { t } = useTranslation()
  const { user } = useUser()
  const [following, setFollowing] = useState(followedByViewer ?? false)
  const [descriptionExpanded, setDescriptionExpanded] = useState(false)

  const { text: shortDescription, isTruncated } = description
    ? truncateText(description, DESCRIPTION_MAX_LENGTH)
    : { text: '', isTruncated: false }

  const onChange = (next: boolean) => {
    setFollowing(next)
    onFollowChange?.(next)
  }

  return (
    <div className={`actor-row actor-row-${variant}`}>
      <Link to={actorPath(actor)} className="actor-row-main">
        <ActorAvatar
          avatarUrl={actor.avatarUrl}
          actorType={actor.actorType}
          size={ActorAvatarSize.SM}
        />
        <span className="actor-row-identity">
          <span className="actor-row-name">{actorDisplayName(actor.name, actor.handle)}</span>
          {actor.actorType === 'user' && <span className="actor-row-handle">@{actor.handle}</span>}
        </span>
      </Link>

      {/* The database forbids the edge (CHECK followerId <> followeeId), so a
          button offering it is a control whose only outcome is a 400. */}
      {actor.id !== user?.id && (
        <FollowButton
          actorId={actor.id}
          following={following}
          onChange={onChange}
          className="actor-row-follow"
        />
      )}

      {description && (
        <p className="actor-row-description">
          {descriptionExpanded ? description : shortDescription}
          {isTruncated && (
            <>
              {' '}
              <button
                type="button"
                className="actor-row-read-more"
                onClick={() => setDescriptionExpanded((expanded) => !expanded)}
              >
                {descriptionExpanded ? t('common.seeLess') : t('common.seeMore')}
              </button>
            </>
          )}
        </p>
      )}
    </div>
  )
}

export default ActorRow
