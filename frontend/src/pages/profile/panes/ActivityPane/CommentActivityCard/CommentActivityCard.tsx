import './CommentActivityCard.scss'
import { type KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconMessage, IconMessageReply, IconMessageCircle } from '@tabler/icons-react'
import { ActorAvatar, ActorAvatarSize, ReactionPicker, ReactionSummary } from '@shared/components'
import useFormat from '@shared/hooks/useFormat'
import { actorPath } from '@utils/actorPath'
import type { ActivityCommentDto } from '@utils/api/activity'

interface CommentActivityCardProps {
  comment: ActivityCommentDto
  onReact: (emoji: string, next: boolean) => void
}

/** Like PostCard, plus a context bar naming the target and minus the copy button. */
const CommentActivityCard = ({ comment, onReact }: CommentActivityCardProps) => {
  const { t } = useTranslation()
  const format = useFormat()
  const navigate = useNavigate()
  const { author, reactions, isReply } = comment

  const ContextIcon = isReply ? IconMessageReply : IconMessage
  const barLabel = isReply
    ? t('profile.activity.commentBar.onComment')
    : t('profile.activity.commentBar.onPost')

  const openPost = () =>
    navigate(`/posts/${comment.postId}`, { state: { scrollToCommentId: comment.id } })

  const onContentKey = (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openPost()
    }
  }

  return (
    <article className="comment-card">
      <div className="comment-card-bar">
        <ContextIcon aria-hidden />
        <span>{barLabel}</span>
      </div>

      <div className="comment-card-body">
        <Link to={actorPath(author)} className="comment-card-author" onClick={(e) => e.stopPropagation()}>
          <ActorAvatar avatarUrl={author.avatarUrl} actorType={author.actorType} size={ActorAvatarSize.SM} />
          <span className="comment-card-identity">
            <span className="comment-card-name">{author.name}</span>
            <span className="comment-card-sub" title={format.date(comment.createdAt, 'dateTime')}>
              {author.actorType === 'user'
                ? `@${author.handle} • ${format.date(comment.createdAt, 'short')}`
                : format.date(comment.createdAt, 'short')}
            </span>
          </span>
        </Link>

        <div
          className="comment-card-content"
          role="link"
          tabIndex={0}
          onClick={openPost}
          onKeyDown={onContentKey}
          aria-label={t('post.openPost')}
        >
          {comment.content}
        </div>

        <div className="comment-card-actions">
          <div className="comment-card-actions-left">
            <ReactionPicker viewerReactions={reactions.viewerReactions ?? []} onToggle={onReact} />

            {!isReply && (
              <button
                type="button"
                className="comment-card-action"
                onClick={openPost}
                aria-label={t('post.openPost')}
              >
                <IconMessageCircle aria-hidden />
              </button>
            )}
          </div>

          <ReactionSummary summary={reactions} onMore={openPost} />
        </div>
      </div>
    </article>
  )
}

export default CommentActivityCard
