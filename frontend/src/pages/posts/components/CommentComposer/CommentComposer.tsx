import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  ActorAvatar,
  ActorAvatarSize,
  ContentEditor,
  ContentEditorMode,
  PostAsSelect,
  PostAsSurface,
} from '@shared/components'
import { useUser } from '@shared/contexts'
import useMyOrganizations from '@shared/hooks/useMyOrganizations'
import usePostAs from '@shared/hooks/usePostAs'
import { canPublishAs } from '@pages/settings/lib/permissions'
import { COMMENT_MAX } from '@utils/constants/validation'
import { parseProblem } from '@utils/api/types'
import { createComment, type ThreadCommentDto } from '@utils/api/posts'

interface CommentComposerProps {
  postId: string
  /** Set to reply to a root comment; the depth cap means this is never a reply's id. */
  parentId?: string
  onCreated: (comment: ThreadCommentDto) => void
  /** Reply composers collapse on cancel; the root composer only clears. */
  onCancel?: () => void
  autoFocus?: boolean
}

/** UI block to write a comment on a post */
const CommentComposer = ({ postId, parentId, onCreated, onCancel, autoFocus }: CommentComposerProps) => {
  const { t } = useTranslation()
  const { user } = useUser()
  const { organizations, refresh } = useMyOrganizations()

  const publishable = organizations.filter((row) => canPublishAs(row.role, PostAsSurface.CONTENT))
  const { value: postAs, setValue: setPostAs, createdForId } = usePostAs(publishable)

  // for avatar
  const actingAs = publishable.find((row) => row.organization.id === postAs)?.organization

  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!user) return null

  const submit = async () => {
    setBusy(true)
    setError(null)
    try {
      const created = await createComment(postId, {
        content: draft.trim(),
        createdForId,
        parentId: parentId ?? null,
      })
      setDraft('')
      onCreated(created)
    } catch (err) {
      const problem = parseProblem(err)
      if (problem.status === 403) refresh()
      setError(problem.detail || t('errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="comment-composer">
      <ContentEditor
        mode={ContentEditorMode.CREATE}
        value={draft}
        onChange={setDraft}
        onSubmit={submit}
        onCancel={() => {
          setDraft('')
          setError(null)
          onCancel?.()
        }}
        submitLabel={t('comments.submit')}
        cancelLabel={t('comments.cancel')}
        placeholder={parentId ? t('feed.replyPlaceholder') : t('feed.placeholder')}
        maxLength={COMMENT_MAX}
        busy={busy}
        error={error}
        autoFocus={autoFocus}
        leading={
          <ActorAvatar
            avatarUrl={actingAs ? actingAs.avatarUrl : user.avatarUrl || null}
            actorType={actingAs ? actingAs.actorType : 'user'}
            size={ActorAvatarSize.XS}
          />
        }
        identity={
          publishable.length > 0 ? (
            <PostAsSelect
              organizations={publishable}
              value={postAs}
              onChange={setPostAs}
              disabled={busy}
            />
          ) : undefined
        }
      />
    </div>
  )
}

export default CommentComposer
