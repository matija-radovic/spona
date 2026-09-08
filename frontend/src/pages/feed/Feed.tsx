import './Feed.scss'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant, PostCard, PostCardVariant } from '@shared/components'
import { useUser } from '@shared/contexts'
import { MQ } from '@shared/css/mediaqueries'
import useMediaQuery from '@shared/hooks/useMediaQuery'
import useOnScreen from '@shared/hooks/useOnScreen'
import { applyReactionDelta } from '@utils/applyReactionDelta'
import { setReaction } from '@utils/api/reactions'
import { fetchFeed, FEED_PAGE_SIZE, type FeedBlockDto } from '@utils/api/feed'
import type { PostDto } from '@utils/api/posts'
import { cachePost, forgetPost, getCachedPost, isPostGone } from '@utils/postCache'
import { getFeedCache, saveFeedScroll, setFeedCache } from '@pages/feed/lib/feedCache'
import { FeedComposer } from '@pages/feed/components/FeedComposer'
import { FeedRail } from '@pages/feed/components/FeedRail'
import { assertExists } from '@utils/assertExists'

type ListState = 'loading' | 'error' | 'ready'

/** `.dl-main` is the page's scroller and is not this page's element. */
const scrollerOf = (node: HTMLElement | null): HTMLElement | null =>
  node?.closest<HTMLElement>('.scroll-container') ?? null

const hydrate = (blocks: FeedBlockDto[]): FeedBlockDto[] =>
  blocks
    .filter((block) => block.kind !== 'post' || !isPostGone(block.post.id))
    .map((block) =>
      block.kind === 'post' ? { ...block, post: getCachedPost(block.post.id) ?? block.post } : block,
    )

const findPost = (blocks: FeedBlockDto[], postId: string): PostDto | undefined => {
  for (const block of blocks) {
    if (block.kind === 'post' && block.post.id === postId) return block.post
  }
  return undefined
}

const Feed = () => {
  const { t } = useTranslation()
  const { user } = useUser()
  assertExists(user, 'Feed rendered without an authenticated user');

  const viewerId = user.id

  const hasRail = useMediaQuery(MQ.exceptTablet)

  const initialCache = useMemo(() => getFeedCache(viewerId), [viewerId])
  const initialBlocks = useMemo(() => (initialCache ? hydrate(initialCache.blocks) : []), [initialCache])

  const [blocks, setBlocks] = useState<FeedBlockDto[]>(initialBlocks)
  const [last, setLast] = useState(initialCache?.last ?? false)
  const [listState, setListState] = useState<ListState>(initialCache ? 'ready' : 'loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const blocksRef = useRef<FeedBlockDto[]>(initialBlocks)
  const pageRef = useRef(initialCache?.page ?? 1)
  const lastRef = useRef(initialCache?.last ?? false)
  const loadingRef = useRef(false)

  const rootRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const writeBlocks = useCallback(
    (next: FeedBlockDto[]) => {
      blocksRef.current = next
      setBlocks(next)
      setFeedCache(viewerId, {
        blocks: next,
        page: pageRef.current,
        last: lastRef.current,
        scrollTop: getFeedCache(viewerId)?.scrollTop ?? 0,
      })
    },
    [viewerId],
  )

  const applyPage = useCallback(
    (res: Awaited<ReturnType<typeof fetchFeed>>, append: boolean) => {
      for (const block of res.content) {
        if (block.kind === 'post') cachePost(block.post)
      }
      pageRef.current = res.page
      lastRef.current = res.last
      setLast(res.last)
      writeBlocks(append ? [...blocksRef.current, ...res.content] : res.content)
    },
    [writeBlocks],
  )

  // First page: fetch on a cache miss, or restore the offset on a cache hit once
  // the rows are back in the DOM and the scroller is tall enough to take it.
  useEffect(() => {
    const cached = getFeedCache(viewerId)
    if (cached) {
      requestAnimationFrame(() => {
        const scroller = scrollerOf(rootRef.current)
        if (scroller) scroller.scrollTop = cached.scrollTop
      })
      return
    }

    let alive = true
    loadingRef.current = true
    fetchFeed(1, FEED_PAGE_SIZE)
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
  }, [viewerId, applyPage, reloadKey])

  useEffect(() => {
    const scroller = scrollerOf(rootRef.current)
    if (!scroller) return
    const onScroll = () => saveFeedScroll(viewerId, scroller.scrollTop)
    scroller.addEventListener('scroll', onScroll, { passive: true })
    return () => scroller.removeEventListener('scroll', onScroll)
  }, [viewerId])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchFeed(pageRef.current + 1, FEED_PAGE_SIZE)
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [applyPage])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && blocks.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const writePost = useCallback(
    (next: PostDto) => {
      cachePost(next)
      writeBlocks(
        blocksRef.current.map((block) =>
          block.kind === 'post' && block.post.id === next.id ? { ...block, post: next } : block,
        ),
      )
    },
    [writeBlocks],
  )

  const onReact = useCallback(
    (postId: string, emoji: string, next: boolean) => {
      const post = findPost(blocksRef.current, postId)
      if (!post) return
      const before = post.reactions
      writePost({ ...post, reactions: applyReactionDelta(before, emoji, next) })
      setReaction('post', postId, emoji, next).catch(() => {
        writePost({ ...post, reactions: before })
      })
    },
    [writePost],
  )

  const onUpdated = useCallback(
    (postId: string, content: string) => {
      const post = findPost(blocksRef.current, postId)
      if (post) writePost({ ...post, content })
    },
    [writePost],
  )

  const onDeleted = useCallback(
    (postId: string) => {
      forgetPost(postId)
      writeBlocks(
        blocksRef.current.filter((block) => block.kind !== 'post' || block.post.id !== postId),
      )
    },
    [writeBlocks],
  )

  const retry = () => {
    setListState('loading')
    setLoadMoreError(false)
    setReloadKey((key) => key + 1)
  }

  return (
    <div className="feed" ref={rootRef}>
      <div className="feed-column">
        <FeedComposer avatarUrl={user.avatarUrl} />

        {listState === 'loading' && blocks.length === 0 && (
          <p className="feed-state">{t('common.loading')}</p>
        )}

        {listState === 'error' && blocks.length === 0 && (
          <div className="feed-state">
            <p>{t('errors.serverError')}</p>
            <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
              {t('jobs.results.retry')}
            </Button>
          </div>
        )}

        {listState === 'ready' && blocks.length === 0 && (
          <div className="feed-state">
            <p className="feed-state-title">{t('feed.empty.title')}</p>
            <p>{t('feed.empty.body')}</p>
            <Button to="/users" variant={ButtonVariant.PRIMARY}>
              {t('feed.empty.action')}
            </Button>
          </div>
        )}

        {blocks.length > 0 && (
          <>
            <div className="feed-list">
              {blocks.map((block) => {
                switch (block.kind) {
                  case 'post':
                    return (
                      <PostCard
                        key={block.post.id}
                        post={block.post}
                        variant={PostCardVariant.PLAIN}
                        onReact={onReact}
                        onUpdated={onUpdated}
                        onDeleted={onDeleted}
                      />
                    )
                  // A block whose kind this build has never heard of renders as
                  // nothing, so a backend can begin serving one before this tab
                  // is reloaded.
                  default:
                    return null
                }
              })}
            </div>

            <div ref={sentinelRef} className="feed-sentinel" aria-hidden />

            {loadingMore && <p className="feed-more">{t('common.loading')}</p>}

            {loadMoreError && (
              <div className="feed-more feed-more-error">
                <span>{t('jobs.results.loadMoreError')}</span>
                <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
                  {t('jobs.results.retry')}
                </Button>
              </div>
            )}

            {last && !loadMoreError && <p className="feed-more">{t('feed.end')}</p>}
          </>
        )}
      </div>

      {hasRail && <FeedRail />}
    </div>
  )
}

export default Feed
