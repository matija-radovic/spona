import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useOnScreen from '@shared/hooks/useOnScreen'
import { Button, ButtonVariant, PostCard } from '@shared/components'
import { fetchActorActivity, type ActivityItemDto, type ActivityKind } from '@utils/api/activity'
import { setReaction } from '@utils/api/reactions'
import { applyReactionDelta } from '@utils/applyReactionDelta'
import { activityCacheKey, getActivityCache, setActivityCache } from '@pages/profile/lib/activityCache'
import { CommentActivityCard } from './CommentActivityCard'

const PAGE_SIZE = 10

type ListState = 'loading' | 'error' | 'ready'

interface ActivityListProps {
  actorId: string
  kind: ActivityKind
}

/**
 * The list engine for one (actorId, kind). Keyed by the parent so a kind switch
 * remounts it with a clean slate that lazy-initialises from the module cache -
 * the JobsView discipline, but scrolling against the page (viewport) scroller.
 * Optimistic reaction updates write through to the cache.
 */
const ActivityList = ({ actorId, kind }: ActivityListProps) => {
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
    fetchActorActivity(actorId, kind, 1, PAGE_SIZE)
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
  }, [actorId, kind, cacheKey, applyPage, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchActorActivity(actorId, kind, pageRef.current + 1, PAGE_SIZE)
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [actorId, kind, applyPage])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const writeItems = useCallback(
    (next: ActivityItemDto[]) => {
      itemsRef.current = next
      setItems(next)
      setActivityCache(cacheKey, {
        items: next,
        page: pageRef.current,
        last: lastRef.current,
        totalElements: totalRef.current,
      })
    },
    [cacheKey],
  )

  const onReact = useCallback(
    (id: string, emoji: string, next: boolean) => {
      const item = itemsRef.current.find((it) => it.id === id)
      if (!item) return
      const targetType = item.kind === 'post' ? 'post' : 'comment'
      const updated = applyReactionDelta(item.reactions, emoji, next)
      writeItems(
        itemsRef.current.map((it) => (it.id === id ? ({ ...it, reactions: updated } as ActivityItemDto) : it)),
      )
      setReaction(targetType, id, emoji, next).catch(() => {
        writeItems(
          itemsRef.current.map((it) =>
            it.id === id ? ({ ...it, reactions: item.reactions } as ActivityItemDto) : it,
          ),
        )
      })
    },
    [writeItems],
  )

  const patchItem = useCallback(
    (id: string, content: string) => {
      writeItems(itemsRef.current.map((it) => (it.id === id ? ({ ...it, content } as ActivityItemDto) : it)))
    },
    [writeItems],
  )

  const dropItem = useCallback(
    (id: string) => {
      const next = itemsRef.current.filter((it) => it.id !== id)
      totalRef.current = Math.max(0, totalRef.current - 1)
      writeItems(next)
    },
    [writeItems],
  )

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
        <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
          {t('jobs.results.retry')}
        </Button>
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
          <PostCard
            key={item.id}
            post={item}
            onReact={onReact}
            onUpdated={patchItem}
            onDeleted={dropItem}
          />
        ) : (
          <CommentActivityCard
            key={item.id}
            comment={item}
            onReact={(emoji, next) => onReact(item.id, emoji, next)}
            onUpdated={(content) => patchItem(item.id, content)}
            onDeleted={() => dropItem(item.id)}
          />
        ),
      )}

      <div ref={sentinelRef} className="activity-pane-sentinel" aria-hidden />

      {loadingMore && <p className="activity-pane-more">{t('common.loading')}</p>}

      {loadMoreError && (
        <div className="activity-pane-more activity-pane-more-error">
          <span>{t('jobs.results.loadMoreError')}</span>
          <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
            {t('jobs.results.retry')}
          </Button>
        </div>
      )}
    </div>
  )
}

export default ActivityList
