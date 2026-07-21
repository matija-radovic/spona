import './ActorSearchRow.scss'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize, FollowButton } from '@shared/components'
import { actorPath } from '@utils/actorPath'
import { truncateText } from '@utils/truncateText'
import type { ActorSearchItemDto } from '@utils/api/profiles'

/** Caps the description at roughly three wrapped lines; anything longer is
 *  truncated to a "read more" toggle. */
const DESCRIPTION_MAX_LENGTH = 160

interface ActorSearchRowProps {
  item: ActorSearchItemDto
}

/**
 * One search result row, laid out as a grid so the Follow button's placement
 * can differ by breakpoint without touching the DOM: on desktop it spans the
 * full card height and sits centered beside it; on mobile (see the SCSS) it's
 * pulled into the name's row instead and shrinks to an icon, since a control
 * centered against a 3-line description reads as floating clutter there.
 *
 * The description is its own grid cell (indented to line up under the
 * identity column) - kept outside the profile `Link` so its "read more"
 * toggle is never a button nested inside an anchor, the same discipline
 * PostCard uses for its actions.
 */
const ActorSearchRow = ({ item }: ActorSearchRowProps) => {
  const { t } = useTranslation()
  const [following, setFollowing] = useState(item.isFollowedByViewer ?? false)
  const [descriptionExpanded, setDescriptionExpanded] = useState(false)

  const description = item.actorType === 'org' ? item.description : null
  const { text: shortDescription, isTruncated } = description
    ? truncateText(description, DESCRIPTION_MAX_LENGTH)
    : { text: '', isTruncated: false }

  return (
    <div className="actor-row">
      <Link to={actorPath(item)} className="actor-row-main">
        <ActorAvatar avatarUrl={item.avatarUrl} actorType={item.actorType} size={ActorAvatarSize.SM} />
        <span className="actor-row-identity">
          <span className="actor-row-name">{item.name}</span>
          {item.actorType === 'user' && <span className="actor-row-handle">@{item.handle}</span>}
        </span>
      </Link>

      <FollowButton
        actorId={item.handle}
        following={following}
        onChange={setFollowing}
        className="actor-row-follow"
      />

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

export default ActorSearchRow
