import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconChevronDown, IconCirclePlus } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components'
import { fetchResponses, type ThreadCommentDto } from '@utils/api/posts'
import { reactToComment } from '@pages/posts/lib/reactToComment'
import { CommentComposer } from '@pages/posts/components/CommentComposer'
import { ThreadComment } from '@pages/posts/components/ThreadComment'

const RESPONSE_PAGE_SIZE = 5

interface RootCommentProps {
  comment: ThreadCommentDto
  canCompose: boolean
  /** This root owns the one reply composer the thread allows to be open. */
  replying: boolean
  onToggleReply: () => void
  onCloseReply: () => void
  onReact: (emoji: string, next: boolean) => void
  onUpdated: (content: string) => void
  onDeleted: (deletedCount: number) => void
  /** A reply arrived or went: `responseCount` and the post's total both move. */
  onResponsesChanged: (delta: number) => void
  anchorRef?: (node: HTMLElement | null) => void
  flashing?: boolean
}

/** A root comment plus the replies hanging off it. */
const RootComment = ({
  comment,
  canCompose,
  replying,
  onToggleReply,
  onCloseReply,
  onReact,
  onUpdated,
  onDeleted,
  onResponsesChanged,
  anchorRef,
  flashing,
}: RootCommentProps) => {
  const { t } = useTranslation()
  // Two lists, because paging walks forward from the oldest reply while anything written here is the newest
  const [fetched, setFetched] = useState<ThreadCommentDto[]>([])
  const [created, setCreated] = useState<ThreadCommentDto[]>([])
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(false)

  const loaded = fetched.length + created.length
  const remaining = Math.max(0, comment.responseCount - loaded)
  const total = remaining + loaded

  const loadMore = async () => {
    setBusy(true)
    setError(false)
    try {
      const res = await fetchResponses(comment.id, page + 1, RESPONSE_PAGE_SIZE)
      setFetched((current) => [...current, ...res.content])
      setPage(res.page)
    } catch {
      setError(true)
    } finally {
      setBusy(false)
    }
  }

  const toggle = () => {
    if (open) {
      setOpen(false)
      setError(false)
      return
    }
    setOpen(true)
    if (!busy && page === 0 && remaining > 0) loadMore()
  }

  const replyProps = (
    reply: ThreadCommentDto,
    write: typeof setFetched,
  ) => ({
    comment: reply,
    onReact: (emoji: string, next: boolean) =>
      reactToComment(reply, emoji, next, (reactions) =>
        write((rows) => rows.map((r) => (r.id === reply.id ? { ...r, reactions } : r))),
      ),
    onUpdated: (content: string) =>
      write((rows) => rows.map((r) => (r.id === reply.id ? { ...r, content } : r))),
    onDeleted: (deletedCount: number) => {
      write((rows) => rows.filter((r) => r.id !== reply.id))
      onResponsesChanged(-deletedCount)
    },
  })

  const hasResponses = total > 0 || replying

  return (
    <ThreadComment
      comment={comment}
      onReact={onReact}
      onUpdated={onUpdated}
      onDeleted={onDeleted}
      onReply={canCompose ? onToggleReply : undefined}
      anchorRef={anchorRef}
      flashing={flashing}
    >
      {hasResponses && (
        <>
          {total > 0 && (
            <Button
              variant={ButtonVariant.GHOST}
              className={`thread-more${open ? ' thread-more-collapse' : ''}`}
              onClick={toggle}
              aria-expanded={open}
            >
              <IconChevronDown className="thread-more-chevron" aria-hidden />
              {open ? t('comments.hideResponses') : t('comments.showResponses', { count: total })}
            </Button>
          )}

          {open && fetched.map((reply) => (
            <ThreadComment key={reply.id} {...replyProps(reply, setFetched)} />
          ))}

          {open && busy && <p className="thread-more-loading">{t('common.loading')}</p>}

          {open && !busy && error && (
            <p className="thread-more-error">{t('jobs.results.loadMoreError')}</p>
          )}

          {open && !busy && remaining > 0 && (
            <Button variant={ButtonVariant.GHOST} className="thread-more" onClick={loadMore}>
              <IconCirclePlus aria-hidden />
              {t('comments.showMore', { count: remaining })}
            </Button>
          )}

          {open && created.map((reply) => (
            <ThreadComment key={reply.id} {...replyProps(reply, setCreated)} />
          ))}

          {replying && (
            <CommentComposer
              postId={comment.postId}
              parentId={comment.id}
              autoFocus
              onCancel={onCloseReply}
              onCreated={(reply) => {
                // Replies read oldest-first, so a new one belongs at the bottom.
                setCreated((current) => [...current, reply])
                setOpen(true)
                onResponsesChanged(1)
                onCloseReply()
              }}
            />
          )}
        </>
      )}
    </ThreadComment>
  )
}

export default RootComment
