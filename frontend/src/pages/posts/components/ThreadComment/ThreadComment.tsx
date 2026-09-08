import { useId, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { IconMessageCircle } from '@tabler/icons-react'
import {
  ActionMenu,
  ActorAvatarSize,
  ActorByline,
  ConfirmDialog,
  ContentEditor,
  contentMenuItems,
  MenuAlign,
  ReactionPicker,
  ReactionSummary,
} from '@shared/components'
import { COMMENT_MAX } from '@utils/constants/validation'
import { parseProblem } from '@utils/api/types'
import { deleteComment, updateComment, type ThreadCommentDto } from '@utils/api/posts'
import useThreadConnector from './useThreadConnector'

interface ThreadCommentProps {
  comment: ThreadCommentDto
  onReact: (emoji: string, next: boolean) => void
  onUpdated: (content: string) => void
  onDeleted: (deletedCount: number) => void
  onReply?: () => void
  /** Rows of the indented block, the container and its svg connector.
   *  Pass nothing when the comment has no thread. */
  children?: ReactNode
  /** Deep-link target, so the thread can scroll it into view. */
  anchorRef?: (node: HTMLElement | null) => void
  flashing?: boolean
}

const ThreadComment = ({
  comment,
  onReact,
  onUpdated,
  onDeleted,
  onReply,
  children,
  anchorRef,
  flashing = false,
}: ThreadCommentProps) => {
  const { t } = useTranslation()
  const { connectorRef, line } = useThreadConnector()
  // Stripped because `useId` wraps its ids in punctuation and this one goes into a `url(#…)` reference.
  const connectorId = `thread-connector-${useId().replace(/[^a-zA-Z0-9]/g, '')}`
  const [draft, setDraft] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const permalink = `/posts/${comment.postId}/comments/${comment.id}`

  const save = async () => {
    if (draft === null) return
    setBusy(true)
    setError(null)
    try {
      const updated = await updateComment(comment.id, draft)
      onUpdated(updated.content)
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
      const { deletedCount } = await deleteComment(comment.id)
      setConfirming(false)
      onDeleted(deletedCount)
    } catch {
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className={`thread-comment${flashing ? ' is-anchor-flash' : ''}`} ref={anchorRef}>
      <div className="thread-comment-author-row">
        <ActorByline actor={comment.author} createdAt={comment.createdAt} size={ActorAvatarSize.XS} />
        <ActionMenu
          align={MenuAlign.END}
          items={contentMenuItems({
            path: permalink,
            copyLabel: t('post.copyLinkComment'),
            editLabel: t('common.edit'),
            deleteLabel: t('common.delete'),
            canEdit: comment.viewerCanEdit,
            canDelete: comment.viewerCanDelete,
            onEdit: () => setDraft(comment.content),
            onDelete: () => setConfirming(true),
          })}
        />
      </div>

      {draft === null ? (
        <div className="thread-comment-content">{comment.content}</div>
      ) : (
        <ContentEditor
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

      <div className="thread-comment-actions">
        <div className="thread-comment-actions-left">
          <ReactionPicker viewerReactions={comment.reactions.viewerReactions ?? []} onToggle={onReact} />

          {onReply && (
            <button type="button" className="thread-comment-action" onClick={onReply}>
              <IconMessageCircle aria-hidden />
              <span>{t('comments.reply')}</span>
            </button>
          )}
        </div>

        <ReactionSummary
          summary={comment.reactions}
          onMore={() => {
            // TODO: onMore reaction handler
          }}
        />
      </div>

      {children && (
        <>
          {line && (
            <svg className="thread-connector" aria-hidden focusable="false">
              <defs>
                <linearGradient
                  id={connectorId}
                  gradientUnits="userSpaceOnUse"
                  x1={line.fadeFrom}
                  y1={0}
                  x2={line.fadeTo}
                  y2={0}
                >
                  <stop offset="0%" stopColor="currentColor" />
                  <stop offset="100%" stopColor="currentColor" stopOpacity={0} />
                </linearGradient>
              </defs>
              <path d={line.d} stroke={`url(#${connectorId})`} />
            </svg>
          )}
          <div className="thread-comment-responses" ref={connectorRef}>
            {children}
          </div>
        </>
      )}

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

export default ThreadComment
