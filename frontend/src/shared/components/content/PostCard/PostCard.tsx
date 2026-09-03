import './PostCard.scss'
import { useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconMessageCircle } from '@tabler/icons-react'
import { ActionMenu, contentMenuItems, MenuAlign } from '@shared/components/overlay/ActionMenu'
import { ActorByline } from '@shared/components/actor/ActorByline'
import { ActorAvatarSize } from '@shared/components/actor/ActorAvatar'
import { ConfirmDialog } from '@shared/components/overlay/ConfirmDialog'
import { ContentEditor, ContentEditorMode } from '@shared/components/content/ContentEditor'
import { ReactionPicker } from '@shared/components/content/ReactionPicker'
import { ReactionSummary } from '@shared/components/content/ReactionSummary'
import { COMMENT_MAX } from '@utils/constants/validation'
import { parseProblem } from '@utils/api/types'
import { deletePost, updatePost } from '@utils/api/posts'
import type { ActivityPostDto } from '@utils/api/activity'

interface PostCardProps {
  post: ActivityPostDto
  /** Toggle a reaction on this post: `next` true to add, false to remove. */
  onReact: (emoji: string, next: boolean) => void
  /** The edited content came back from the server; patch the list. */
  onUpdated?: (content: string) => void
  onDeleted?: () => void
}

const PostCard = ({ post, onReact, onUpdated, onDeleted }: PostCardProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { author, reactions } = post

  const [draft, setDraft] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const openPost = () => navigate(`/posts/${post.id}`)
  const onContentKey = (event: KeyboardEvent) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      openPost()
    }
  }

  const save = async () => {
    if (draft === null) return
    setBusy(true)
    setError(null)
    try {
      const updated = await updatePost(post.id, draft)
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
      await deletePost(post.id)
      setConfirming(false)
      onDeleted?.()
    } catch {
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="post-card">
      <div className="post-card-author-row">
        <ActorByline actor={author} createdAt={post.createdAt} size={ActorAvatarSize.SM} />
        <ActionMenu
          align={MenuAlign.END}
          items={contentMenuItems({
            path: `/posts/${post.id}`,
            copyLabel: t('common.copyLink'),
            editLabel: t('post.edit'),
            deleteLabel: t('post.delete'),
            canEdit: post.viewerCanEdit && onUpdated !== undefined,
            canDelete: post.viewerCanDelete && onDeleted !== undefined,
            onEdit: () => setDraft(post.content),
            onDelete: () => setConfirming(true),
          })}
        />
      </div>

      {draft === null ? (
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
          ariaLabel={t('post.editPost')}
          maxLength={COMMENT_MAX}
          busy={busy}
          error={error}
          autoFocus
        />
      )}

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
        </div>

        <ReactionSummary summary={reactions} onMore={openPost} />
      </div>

      <ConfirmDialog
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={remove}
        title={t('post.delete')}
        body={<p>{t('post.deleteConfirm')}</p>}
        confirmLabel={t('common.delete')}
        danger
        busy={busy}
      />
    </article>
  )
}

export default PostCard
