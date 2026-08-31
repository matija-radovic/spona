import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconChevronDown } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components'
import { fetchResponses, type CommentContextDto, type ThreadCommentDto } from '@utils/api/posts'
import { reactToComment } from '@pages/posts/lib/reactToComment'
import { CommentComposer } from '@pages/posts/components/CommentComposer'
import { ThreadComment } from '@pages/posts/components/ThreadComment'
import RootComment from './RootComment'

/** How many siblings each window reaches for. */
const WINDOW = 5

const previousWindow = (siblingIndex: number) => {
  const offset = Math.max(0, siblingIndex - WINDOW)
  return { offset, count: siblingIndex - offset }
}

const nextWindow = (siblingIndex: number, siblingTotal: number) => {
  const offset = siblingIndex + 1
  return { offset, count: Math.max(0, Math.min(WINDOW, siblingTotal - offset)) }
}

interface AnchoredCommentProps {
  context: CommentContextDto
  canCompose: boolean
  replying: boolean
  onToggleReply: () => void
  onCloseReply: () => void
  onResponsesChanged: (delta: number) => void
  onDeleted: (deletedCount: number) => void
  anchorRef: (node: HTMLElement | null) => void
  flashing: boolean
}

/** The comment deep link block */
const AnchoredComment = ({
  context,
  canCompose,
  replying,
  onToggleReply,
  onCloseReply,
  onResponsesChanged,
  onDeleted,
  anchorRef,
  flashing,
}: AnchoredCommentProps) => {
  const { t } = useTranslation()
  const [comment, setComment] = useState(context.comment)
  const [parent, setParent] = useState(context.parent)
  const [before, setBefore] = useState<ThreadCommentDto[]>([])
  const [after, setAfter] = useState<ThreadCommentDto[]>([])
  const [created, setCreated] = useState<ThreadCommentDto[]>([])
  const [previousLoaded, setPreviousLoaded] = useState(false)
  const [nextLoaded, setNextLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [gone, setGone] = useState(false)

  const prev = previousWindow(context.siblingIndex)
  const next = nextWindow(context.siblingIndex, context.siblingTotal)

  const reactToAnchor = (emoji: string, on: boolean) =>
    reactToComment(comment, emoji, on, (reactions) =>
      setComment((current) => ({ ...current, reactions })),
    )

  const updateAnchor = (content: string) => setComment((current) => ({ ...current, content }))

  const removeAnchor = (deletedCount: number) => {
    setGone(true)
    onDeleted(deletedCount)
  }

  const siblingProps = (reply: ThreadCommentDto, write: typeof setBefore) => ({
    comment: reply,
    onReact: (emoji: string, on: boolean) =>
      reactToComment(reply, emoji, on, (reactions) =>
        write((rows) => rows.map((r) => (r.id === reply.id ? { ...r, reactions } : r))),
      ),
    onUpdated: (content: string) =>
      write((rows) => rows.map((r) => (r.id === reply.id ? { ...r, content } : r))),
    onDeleted: (deletedCount: number) => {
      write((rows) => rows.filter((r) => r.id !== reply.id))
      onResponsesChanged(-deletedCount)
    },
  })

  const loadWindow = async (side: 'previous' | 'next') => {
    if (!parent) return
    const window = side === 'previous' ? prev : next
    setBusy(true)
    try {
      const res = await fetchResponses(parent.id, 1, window.offset + window.count)
      const slice = res.content.slice(window.offset, window.offset + window.count)
      if (side === 'previous') {
        setBefore(slice)
        setPreviousLoaded(true)
      } else {
        setAfter(slice)
        setNextLoaded(true)
      }
    } catch {
      /* a window is an enrichment, not the point of the page - leave the control */
    } finally {
      setBusy(false)
    }
  }

  if (gone) return null

  // A root-level deep link is just a root comment, highlighted
  if (!parent) {
    return (
      <div className="comment-thread-anchor">
        <RootComment
          comment={comment}
          canCompose={canCompose}
          replying={replying}
          onToggleReply={onToggleReply}
          onCloseReply={onCloseReply}
          onReact={reactToAnchor}
          onUpdated={updateAnchor}
          onDeleted={removeAnchor}
          onResponsesChanged={onResponsesChanged}
          anchorRef={anchorRef}
          flashing={flashing}
        />
      </div>
    )
  }

  // A reply on other side is a fcking...
  return (
    <div className="comment-thread-anchor">
      <ThreadComment
        comment={parent}
        onReact={(emoji, on) =>
          reactToComment(parent, emoji, on, (reactions) =>
            setParent((current) => (current ? { ...current, reactions } : current)),
          )
        }
        onUpdated={(content) => setParent((current) => (current ? { ...current, content } : current))}
        onDeleted={removeAnchor}
        onReply={canCompose ? onToggleReply : undefined}
      >
        <>
          {prev.count > 0 && !previousLoaded && (
            <Button
              variant={ButtonVariant.GHOST}
              className="thread-more"
              onClick={() => loadWindow('previous')}
              disabled={busy}
            >
              <IconChevronDown className="thread-more-chevron" aria-hidden />
              {t('comments.showPrevious', { count: prev.count })}
            </Button>
          )}

          {before.map((reply) => (
            <ThreadComment key={reply.id} {...siblingProps(reply, setBefore)} />
          ))}

          <ThreadComment
            comment={comment}
            onReact={reactToAnchor}
            onUpdated={updateAnchor}
            onDeleted={removeAnchor}
            anchorRef={anchorRef}
            flashing={flashing}
          />

          {after.map((reply) => (
            <ThreadComment key={reply.id} {...siblingProps(reply, setAfter)} />
          ))}

          {next.count > 0 && !nextLoaded && (
            <Button
              variant={ButtonVariant.GHOST}
              className="thread-more"
              onClick={() => loadWindow('next')}
              disabled={busy}
            >
              <IconChevronDown className="thread-more-chevron" aria-hidden />
              {t('comments.showNext', { count: next.count })}
            </Button>
          )}

          {created.map((reply) => (
            <ThreadComment key={reply.id} {...siblingProps(reply, setCreated)} />
          ))}

          {replying && (
            <CommentComposer
              postId={parent.postId}
              parentId={parent.id}
              autoFocus
              onCancel={onCloseReply}
              onCreated={(reply) => {
                setCreated((current) => [...current, reply])
                onResponsesChanged(1)
                onCloseReply()
              }}
            />
          )}
        </>
      </ThreadComment>
    </div>
  )
}

export default AnchoredComment
