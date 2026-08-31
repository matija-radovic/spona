import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'
import useOnScreen from '@shared/hooks/useOnScreen'
import { useUser } from '@shared/contexts'
import { fetchComments, type CommentContextDto, type CommentSort, type ThreadCommentDto } from '@utils/api/posts'
import { FilterCapsules, type CapsuleOption } from '@pages/profile/components/FilterCapsules'
import {
  commentsCacheKey,
  COMMENTS_PAGE_SIZE,
  DEFAULT_COMMENT_SORT,
  evictPostComments,
  getCommentsCache,
  setCommentsCache,
} from '@pages/posts/lib/commentsCache'
import { reactToComment } from '@pages/posts/lib/reactToComment'
import { CommentComposer } from '@pages/posts/components/CommentComposer'
import AnchoredComment from './AnchoredComment'
import RootComment from './RootComment'

const FLASH_DELAY_MS = 400
const FLASH_MS = 1500

type ListState = 'loading' | 'error' | 'ready'

interface CommentThreadProps {
  postId: string
  /** roots + replies, owned by the page so the post's own count stays in step. */
  commentCount: number
  onCommentCountChange: (delta: number) => void
  /** Deep link: the linked comment plus its parent and sibling position. */
  anchor: CommentContextDto | null
}

/** The composer, the sort control and the root list. */
const CommentThread = ({ postId, commentCount, onCommentCountChange, anchor }: CommentThreadProps) => {
  const { t } = useTranslation()
  const { isAuthenticated } = useUser()

  const [sort, setSort] = useState<CommentSort>(DEFAULT_COMMENT_SORT)
  const [initial] = useState(() => getCommentsCache(commentsCacheKey(postId, DEFAULT_COMMENT_SORT)))

  const [items, setItems] = useState<ThreadCommentDto[]>(initial?.items ?? [])
  const [last, setLast] = useState(initial?.last ?? false)
  const [listState, setListState] = useState<ListState>(initial ? 'ready' : 'loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [replyingTo, setReplyingTo] = useState<string | null>(null)
  const [anchorNode, setAnchorNode] = useState<HTMLElement | null>(null)
  const [flashing, setFlashing] = useState(false)

  const itemsRef = useRef<ThreadCommentDto[]>(initial?.items ?? [])
  const pageRef = useRef(initial?.page ?? 1)
  const lastRef = useRef(initial?.last ?? false)
  const totalRef = useRef(initial?.totalElements ?? 0)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const cacheKey = commentsCacheKey(postId, sort)
  const exclude = anchor?.comment.id

  const applyPage = useCallback(
    (res: Awaited<ReturnType<typeof fetchComments>>, append: boolean) => {
      const merged = append ? [...itemsRef.current, ...res.content] : res.content
      itemsRef.current = merged
      pageRef.current = res.page
      lastRef.current = res.last
      totalRef.current = res.totalElements
      setItems(merged)
      setLast(res.last)
      setCommentsCache(cacheKey, {
        items: merged,
        page: res.page,
        last: res.last,
        totalElements: res.totalElements,
      })
    },
    [cacheKey],
  )

  // First page: fetch on a cache miss (a hit already lazy-inited the state).
  useEffect(() => {
    if (getCommentsCache(cacheKey)) return

    let alive = true
    loadingRef.current = true
    fetchComments(postId, { page: 1, size: COMMENTS_PAGE_SIZE, sort, exclude })
      .then((res) => {
        if (!alive) return
        applyPage(res, false)
        setListState('ready')
      })
      .catch(() => {
        if (alive) setListState('error')
      })
      .finally(() => {
        loadingRef.current = false
      })
    return () => {
      alive = false
    }
  }, [postId, sort, exclude, cacheKey, applyPage, reloadKey])

  useEffect(() => {
    if (!anchor || !anchorNode) return

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    anchorNode.scrollIntoView({ block: 'center', behavior: reduced ? 'auto' : 'smooth' })

    const delay = reduced ? 0 : FLASH_DELAY_MS
    const start = window.setTimeout(() => setFlashing(true), delay)
    const stop = window.setTimeout(() => setFlashing(false), delay + FLASH_MS)
    return () => {
      window.clearTimeout(start)
      window.clearTimeout(stop)
      setFlashing(false)
    }
  }, [anchor, anchorNode])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchComments(postId, {
        page: pageRef.current + 1,
        size: COMMENTS_PAGE_SIZE,
        sort,
        exclude,
      })
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [postId, sort, exclude, applyPage])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const writeItems = (next: ThreadCommentDto[]) => {
    itemsRef.current = next
    setItems(next)
    setCommentsCache(cacheKey, {
      items: next,
      page: pageRef.current,
      last: lastRef.current,
      totalElements: totalRef.current,
    })
  }

  const patch = (id: string, patchRow: (row: ThreadCommentDto) => ThreadCommentDto) =>
    writeItems(itemsRef.current.map((row) => (row.id === id ? patchRow(row) : row)))

  const onSortChange = (next: CommentSort) => {
    if (next === sort) return
    evictPostComments(postId)
    itemsRef.current = []
    pageRef.current = 1
    lastRef.current = false
    totalRef.current = 0
    setItems([])
    setLast(false)
    setLoadMoreError(false)
    setListState('loading')
    setSort(next)
  }

  const retry = () => {
    setListState('loading')
    setLoadMoreError(false)
    setReloadKey((key) => key + 1)
  }

  const capsules: CapsuleOption[] = [
    { key: 'top', label: t('comments.sort.top') },
    { key: 'new', label: t('comments.sort.new') },
    { key: 'old', label: t('comments.sort.old') },
  ]

  const empty = listState === 'ready' && items.length === 0 && !anchor

  return (
    <>
      {isAuthenticated && (
        <section className="post-page-composer">
          <CommentComposer
            postId={postId}
            onCreated={(created) => {
              // Newest first at the top of the root list
              writeItems([created, ...itemsRef.current])
              onCommentCountChange(1)
            }}
          />
        </section>
      )}

      <section className="post-page-thread">
        <div className="post-page-thread-head">
          <FilterCapsules
            options={capsules}
            active={sort}
            onChange={(key) => onSortChange(key as CommentSort)}
            ariaLabel={t('comments.title', { count: commentCount })}
          />
          <span className="post-page-thread-count">{t('comments.title', { count: commentCount })}</span>
        </div>

        {anchor && (
          <AnchoredComment
            context={anchor}
            canCompose={isAuthenticated}
            replying={replyingTo === anchor.comment.id}
            onToggleReply={() =>
              setReplyingTo((current) => (current === anchor.comment.id ? null : anchor.comment.id))
            }
            onCloseReply={() => setReplyingTo(null)}
            onResponsesChanged={onCommentCountChange}
            onDeleted={(deletedCount) => onCommentCountChange(-deletedCount)}
            anchorRef={setAnchorNode}
            flashing={flashing}
          />
        )}

        {listState === 'loading' && items.length === 0 && (
          <p className="post-page-thread-state">{t('common.loading')}</p>
        )}

        {listState === 'error' && items.length === 0 && (
          <div className="post-page-thread-state">
            <p>{t('errors.serverError')}</p>
            <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
              {t('jobs.results.retry')}
            </Button>
          </div>
        )}

        {empty && <p className="post-page-thread-state">{t('comments.empty')}</p>}

        {items.length > 0 && (
          <div className="post-page-thread-list">
            {items.map((comment) => (
              <RootComment
                key={comment.id}
                comment={comment}
                canCompose={isAuthenticated}
                replying={replyingTo === comment.id}
                onToggleReply={() =>
                  setReplyingTo((current) => (current === comment.id ? null : comment.id))
                }
                onCloseReply={() => setReplyingTo(null)}
                onReact={(emoji, next) =>
                  reactToComment(comment, emoji, next, (reactions) =>
                    patch(comment.id, (row) => ({ ...row, reactions })),
                  )
                }
                onUpdated={(content) => patch(comment.id, (row) => ({ ...row, content }))}
                onDeleted={(deletedCount) => {
                  writeItems(itemsRef.current.filter((row) => row.id !== comment.id))
                  onCommentCountChange(-deletedCount)
                }}
                onResponsesChanged={(delta) => {
                  patch(comment.id, (row) => ({
                    ...row,
                    responseCount: Math.max(0, row.responseCount + delta),
                  }))
                  onCommentCountChange(delta)
                }}
              />
            ))}
          </div>
        )}

        <div ref={sentinelRef} className="post-page-thread-sentinel" aria-hidden />

        {loadingMore && <p className="post-page-thread-more">{t('common.loading')}</p>}

        {loadMoreError && (
          <div className="post-page-thread-more thread-more-error">
            <span>{t('jobs.results.loadMoreError')}</span>
            <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
              {t('jobs.results.retry')}
            </Button>
          </div>
        )}
      </section>
    </>
  )
}

export default CommentThread
