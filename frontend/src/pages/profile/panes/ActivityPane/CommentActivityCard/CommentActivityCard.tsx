import './CommentActivityCard.scss'
import { useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconMessage, IconMessageReply, IconMessageCircle } from '@tabler/icons-react'
import {
  ActionMenu,
  ActorAvatarSize,
  ActorByline,
  ConfirmDialog,
  ContentEditor,
  ContentEditorMode,
  contentMenuItems,
  MenuAlign,
  ReactionPicker,
  ReactionSummary,
} from '@shared/components'
import { COMMENT_MAX } from '@utils/constants/validation'
import { parseProblem } from '@utils/api/types'
import { deleteComment, updateComment } from '@utils/api/posts'
import type { ActivityCommentDto } from '@utils/api/activity'

interface CommentActivityCardProps {
  comment: ActivityCommentDto
  onReact: (emoji: string, next: boolean) => void
  /** The edited content came back from the server; patch the list. */
  onUpdated?: (content: string) => void
  onDeleted?: () => void
}

/** Like PostCard, plus a context bar naming the target. */
const CommentActivityCard = ({ comment, onReact, onUpdated, onDeleted }: CommentActivityCardProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { author, reactions, isReply } = comment

  const [draft, setDraft] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const ContextIcon = isReply ? IconMessageReply : IconMessage
  const barLabel = isReply
    ? t('profile.activity.commentBar.onComment')
    : t('profile.activity.commentBar.onPost')

  const permalink = `/posts/${comment.postId}/comments/${comment.id}`
  const openComment = () => navigate(permalink)

  const onContentKey = (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openComment()
    }
  }

  const save = async () => {
    if (draft === null) return
    setBusy(true)
    setError(null)
    try {
      const updated = await updateComment(comment.id, draft)
      onUpdated?.(updated.content)
      setDraft(null)
    } catch (err) {
      setError(parseProblem(err).detail || t('errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    try {
      await deleteComment(comment.id)
      setConfirming(false)
      onDeleted?.()
    } catch {
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="comment-card">
      <div className="comment-card-bar">
        <ContextIcon aria-hidden />
        <span>{barLabel}</span>
      </div>

      <div className="comment-card-body">
        <div className="comment-card-author-row">
          <ActorByline actor={author} createdAt={comment.createdAt} size={ActorAvatarSize.SM} />
          <ActionMenu
            align={MenuAlign.END}
            items={contentMenuItems({
              path: permalink,
              copyLabel: t('post.copyLinkComment'),
              editLabel: t('common.edit'),
              deleteLabel: t('common.delete'),
              canEdit: comment.viewerCanEdit && onUpdated !== undefined,
              canDelete: comment.viewerCanDelete && onDeleted !== undefined,
              onEdit: () => setDraft(comment.content),
              onDelete: () => setConfirming(true),
            })}
          />
        </div>

        {draft === null ? (
          <div
            className="comment-card-content"
            role="link"
            tabIndex={0}
            onClick={openComment}
            onKeyDown={onContentKey}
            aria-label={t('post.openPost')}
          >
            {comment.content}
          </div>
        ) : (
          <ContentEditor
            mode={ContentEditorMode.EDIT}
            value={draft}
            onChange={setDraft}
            onSubmit={save}
            onCancel={() => {
              setDraft(null)
              setError(null)
            }}
            submitLabel={t('post.editSave')}
            cancelLabel={t('post.editCancel')}
            ariaLabel={t('comments.edit')}
            maxLength={COMMENT_MAX}
            busy={busy}
            error={error}
            autoFocus
          />
        )}

        <div className="comment-card-actions">
          <div className="comment-card-actions-left">
            <ReactionPicker viewerReactions={reactions.viewerReactions ?? []} onToggle={onReact} />

            {!isReply && (
              <button
                type="button"
                className="comment-card-action"
                onClick={openComment}
                aria-label={t('post.openPost')}
              >
                <IconMessageCircle aria-hidden />
              </button>
            )}
          </div>

          <ReactionSummary summary={reactions} onMore={openComment} />
        </div>
      </div>

      <ConfirmDialog
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={remove}
        title={
          comment.responseCount > 0
            ? t('comments.deleteWithReplies', { count: comment.responseCount })
            : t('comments.deleteConfirm')
        }
        confirmLabel={t('common.delete')}
        danger
        busy={busy}
      />
    </article>
  )
}

export default CommentActivityCard
