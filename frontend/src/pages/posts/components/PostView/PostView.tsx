import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  ActionMenu,
  ActorAvatarSize,
  ActorByline,
  ConfirmDialog,
  ContentEditor,
  contentMenuItems,
  CopyLinkButton,
  MenuAlign,
  ReactionPicker,
  ReactionSummary,
} from '@shared/components'
import { COMMENT_MAX } from '@utils/constants/validation'
import { parseProblem } from '@utils/api/types'
import { problemMessage } from '@utils/api/problemMessage'
import { updatePost, type PostDto } from '@utils/api/posts'

interface PostViewProps {
  post: PostDto
  onReact: (emoji: string, next: boolean) => void
  onUpdated: (content: string) => void
  /** The page owns where a delete navigates to. */
  onDelete: () => void
}

const PostView = ({ post, onReact, onUpdated, onDelete }: PostViewProps) => {
  const { t } = useTranslation()
  const [draft, setDraft] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)

  const save = async () => {
    if (draft === null) return
    setBusy(true)
    setError(null)
    try {
      const updated = await updatePost(post.id, draft)
      onUpdated(updated.content)
      setDraft(null)
    } catch (err) {
      setError(problemMessage(parseProblem(err), t))
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="post-detail">
      <div className="post-detail-author-row">
        <ActorByline actor={post.author} createdAt={post.createdAt} size={ActorAvatarSize.SM} />
        <ActionMenu
          align={MenuAlign.END}
          items={contentMenuItems({
            path: `/posts/${post.id}`,
            copyLabel: t('common.copyLink'),
            editLabel: t('post.edit'),
            deleteLabel: t('post.delete'),
            canEdit: post.viewerCanEdit,
            canDelete: post.viewerCanDelete,
            onEdit: () => setDraft(post.content),
            onDelete: () => setConfirming(true),
          })}
        />
      </div>

      {draft === null ? (
        <div className="post-detail-content">{post.content}</div>
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
          ariaLabel={t('post.editPost')}
          maxLength={COMMENT_MAX}
          busy={busy}
          error={error}
          autoFocus
        />
      )}

      <div className="post-detail-actions">
        <div className="post-detail-actions-left">
          <ReactionPicker viewerReactions={post.reactions.viewerReactions ?? []} onToggle={onReact} />
          <CopyLinkButton url={`${window.location.origin}/posts/${post.id}`} />
        </div>

        <ReactionSummary summary={post.reactions} targetType="post" targetId={post.id} />
      </div>

      <ConfirmDialog
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => {
          setConfirming(false)
          onDelete()
        }}
        title={t('post.delete')}
        body={<p>{t('post.deleteConfirm')}</p>}
        confirmLabel={t('common.delete')}
        danger
      />
    </article>
  )
}

export default PostView
