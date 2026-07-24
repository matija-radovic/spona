import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useOnScreen from '@shared/hooks/useOnScreen'
import { PostCard } from '@shared/components'
import {
  fetchActorActivity,
  setReaction,
  type ActivityItemDto,
  type ActivityKind,
  type ReactionSummaryDto,
} from '@utils/api/activity'
import type { ActorRefDto } from '@utils/api/profiles'
import { activityCacheKey, getActivityCache, setActivityCache } from '@pages/profile/lib/activityCache'
import { CommentActivityCard } from './CommentActivityCard'

const PAGE_SIZE = 10

type ListState = 'loading' | 'error' | 'ready'

/** Recompute a summary from a ±1 delta on the picked emoji - honest and simple:
 *  we do not re-rank beyond what is knowable client-side (§7.3). */
const applyReactionDelta = (
  summary: ReactionSummaryDto,
  emoji: string,
  next: boolean,
): ReactionSummaryDto => {
  const viewer = new Set(summary.viewerReactions ?? [])
  if (next) viewer.add(emoji)
  else viewer.delete(emoji)

  const total = Math.max(0, summary.total + (next ? 1 : -1))
  const topIndex = summary.top.findIndex((entry) => entry.emoji === emoji)
  let top = summary.top.map((entry) => ({ ...entry }))
  let othersCount = summary.othersCount

  if (topIndex >= 0) {
    top[topIndex] = { ...top[topIndex], count: top[topIndex].count + (next ? 1 : -1) }
    top = top.filter((entry) => entry.count > 0)
  } else {
    othersCount = Math.max(0, othersCount + (next ? 1 : -1))
  }

  return { top, othersCount, total, viewerReactions: [...viewer] }
}

interface ActivityListProps {
  actorId: string
  owner: ActorRefDto
  kind: ActivityKind
}

/**
 * The list engine for one (actorId, kind). Keyed by the parent so a kind switch
 * remounts it with a clean slate that lazy-initialises from the module cache -
 * the JobsView discipline, but scrolling against the page (viewport) scroller.
 * Optimistic reaction updates write through to the cache.
 */
const ActivityList = ({ actorId, owner, kind }: ActivityListProps) => {
  const { t } = useTranslation()
  const cacheKey = activityCacheKey(actorId, kind)
  const initial = useMemo(() => getActivityCache(cacheKey), [cacheKey])

  const [items, setItems] = useState<ActivityItemDto[]>(initial?.items ?? [])
  const [last, setLast] = useState(initial?.last ?? false)
  const [listState, setListState] = useState<ListState>(initial ? 'ready' : 'loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const itemsRef = useRef<ActivityItemDto[]>(initial?.items ?? [])
  const pageRef = useRef(initial?.page ?? 1)
  const lastRef = useRef(initial?.last ?? false)
  const totalRef = useRef(initial?.totalElements ?? 0)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const applyPage = useCallback(
    (res: Awaited<ReturnType<typeof fetchActorActivity>>, append: boolean) => {
      const merged = append ? [...itemsRef.current, ...res.content] : res.content
      itemsRef.current = merged
      pageRef.current = res.page
      lastRef.current = res.last
      totalRef.current = res.totalElements
      setItems(merged)
      setLast(res.last)
      setActivityCache(cacheKey, {
        items: merged,
        page: res.page,
        last: res.last,
        totalElements: res.totalElements,
      })
    },
    [cacheKey],
  )

  // First page: fetch on a cache miss (a cache hit already lazy-inited state).
  useEffect(() => {
    if (getActivityCache(cacheKey)) return

    let alive = true
    loadingRef.current = true
    fetchActorActivity(actorId, kind, owner, 1, PAGE_SIZE)
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
  }, [actorId, kind, owner, cacheKey, applyPage, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchActorActivity(actorId, kind, owner, pageRef.current + 1, PAGE_SIZE)
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [actorId, kind, owner, applyPage])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const writeItems = (next: ActivityItemDto[]) => {
    itemsRef.current = next
    setItems(next)
    setActivityCache(cacheKey, {
      items: next,
      page: pageRef.current,
      last: lastRef.current,
      totalElements: totalRef.current,
    })
  }

  const onReact = (item: ActivityItemDto, emoji: string, next: boolean) => {
    const targetType = item.kind === 'post' ? 'post' : 'comment'
    const updated = applyReactionDelta(item.reactions, emoji, next)
    writeItems(
      itemsRef.current.map((it) => (it.id === item.id ? ({ ...it, reactions: updated } as ActivityItemDto) : it)),
    )
    setReaction(targetType, item.id, emoji, next).catch(() => {
      writeItems(
        itemsRef.current.map((it) =>
          it.id === item.id ? ({ ...it, reactions: item.reactions } as ActivityItemDto) : it,
        ),
      )
    })
  }

  const retry = () => {
    setListState('loading')
    setLoadMoreError(false)
    setReloadKey((k) => k + 1)
  }

  if (listState === 'loading' && items.length === 0) {
    return <p className="activity-pane-state">{t('common.loading')}</p>
  }

  if (listState === 'error' && items.length === 0) {
    return (
      <div className="activity-pane-state">
        <p>{t('errors.serverError')}</p>
        <button type="button" className="activity-pane-retry" onClick={retry}>
          {t('jobs.results.retry')}
        </button>
      </div>
    )
  }

  if (listState === 'ready' && items.length === 0) {
    return <p className="activity-pane-empty">{t('profile.activity.empty')}</p>
  }

  return (
    <div className="activity-pane-list">
      {items.map((item) =>
        item.kind === 'post' ? (
          <PostCard key={item.id} post={item} onReact={(emoji, next) => onReact(item, emoji, next)} />
        ) : (
          <CommentActivityCard
            key={item.id}
            comment={item}
            onReact={(emoji, next) => onReact(item, emoji, next)}
          />
        ),
      )}

      <div ref={sentinelRef} className="activity-pane-sentinel" aria-hidden />

      {loadingMore && <p className="activity-pane-more">{t('common.loading')}</p>}

      {loadMoreError && (
        <div className="activity-pane-more activity-pane-more-error">
          <span>{t('jobs.results.loadMoreError')}</span>
          <button type="button" className="activity-pane-retry" onClick={loadMore}>
            {t('jobs.results.retry')}
          </button>
        </div>
      )}
    </div>
  )
}

export default ActivityList
