import './PostCard.scss'
import { type KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconMessageCircle } from '@tabler/icons-react'
import { ActorAvatar, ActorAvatarSize } from '@shared/components/ActorAvatar'
import { CopyLinkButton } from '@shared/components/CopyLinkButton'
import { ReactionPicker } from '@shared/components/ReactionPicker'
import { ReactionSummary } from '@shared/components/ReactionSummary'
import useFormat from '@shared/hooks/useFormat'
import { actorPath } from '@utils/actorPath'
import type { ActivityPostDto } from '@utils/api/activity'

interface PostCardProps {
  post: ActivityPostDto
  /** Toggle a reaction on this post: `next` true to add, false to remove. */
  onReact: (emoji: string, next: boolean) => void
}

/** Only the content block navigates, not the whole card, so actions never misfire. */
const PostCard = ({ post, onReact }: PostCardProps) => {
  const { t } = useTranslation()
  const format = useFormat()
  const navigate = useNavigate()
  const { author, reactions } = post

  const openPost = () => navigate(`/posts/${post.id}`)
  const onContentKey = (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openPost()
    }
  }

  return (
    <article className="post-card">
      <Link to={actorPath(author)} className="post-card-author" onClick={(e) => e.stopPropagation()}>
        <ActorAvatar avatarUrl={author.avatarUrl} actorType={author.actorType} size={ActorAvatarSize.SM} />
        <span className="post-card-identity">
          <span className="post-card-name">{author.name}</span>
          <span className="post-card-sub" title={format.date(post.createdAt, 'dateTime')}>
            {author.actorType === 'user'
              ? `@${author.handle} • ${format.date(post.createdAt, 'short')}`
              : format.date(post.createdAt, 'short')}
          </span>
        </span>
      </Link>

      <div
        className="post-card-content"
        role="link"
        tabIndex={0}
        onClick={openPost}
        onKeyDown={onContentKey}
        aria-label={t('post.openPost')}
      >
        {post.content}
      </div>

      <div className="post-card-actions">
        <div className="post-card-actions-left">
          <ReactionPicker viewerReactions={reactions.viewerReactions ?? []} onToggle={onReact} />

          <button
            type="button"
            className="post-card-action"
            onClick={openPost}
            aria-label={t('post.comments', { count: post.commentCount })}
          >
            <IconMessageCircle aria-hidden />
            <span>{post.commentCount}</span>
          </button>

          <CopyLinkButton url={`${window.location.origin}/posts/${post.id}`} />
        </div>

        <ReactionSummary summary={reactions} onMore={openPost} />
      </div>
    </article>
  )
}

export default PostCard
