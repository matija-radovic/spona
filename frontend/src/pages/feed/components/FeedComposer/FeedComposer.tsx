import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ActorAvatar, ActorAvatarSize } from '@shared/components'

interface FeedComposerProps {
  avatarUrl: string | null
}

/** link to create a post */
const FeedComposer = ({ avatarUrl }: FeedComposerProps) => {
  const { t } = useTranslation()

  const [label] = useState(() => {
    const labels: unknown = t('feed.composerLabels', { returnObjects: true })
    const list = Array.isArray(labels) ? (labels as string[]) : []
    return list.length > 0 ? list[Math.floor(Math.random() * list.length)] : t('post.create.title')
  })

  return (
    <div className="feed-composer">
      <ActorAvatar avatarUrl={avatarUrl} actorType="user" size={ActorAvatarSize.SM} alt="" />
      <Link to="/posts/new" className="feed-composer-link" aria-label={t('feed.composerAria')}>
        {label}
      </Link>
    </div>
  )
}

export default FeedComposer
