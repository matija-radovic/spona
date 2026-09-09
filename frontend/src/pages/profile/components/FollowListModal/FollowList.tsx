import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ActorRow, ActorRowVariant } from '@shared/components'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'
import useOnScreen from '@shared/hooks/useOnScreen'
import {
  fetchFollowList,
  FOLLOW_PAGE_SIZE,
  type FollowActorDto,
  type FollowDirection,
} from '@utils/api/profiles'

type ListState = 'loading' | 'error' | 'ready'

interface FollowListProps {
  actorId: string
  direction: FollowDirection
  /** Debounced, and half of the key the parent remounts this on. */
  query: string
  /** Reports a row's flip, so the reader's own following count can move. */
  onFollowChange?: (next: boolean) => void
}

const FollowList = ({ actorId, direction, query, onFollowChange }: FollowListProps) => {
  const { t } = useTranslation()

  const [items, setItems] = useState<FollowActorDto[]>([])
  const [listState, setListState] = useState<ListState>('loading')
  const [last, setLast] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const itemsRef = useRef<FollowActorDto[]>([])
  const pageRef = useRef(1)
  const lastRef = useRef(false)
  const loadingRef = useRef(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useGhostScrollbar(rootRef)

  useEffect(() => {
    let alive = true
    loadingRef.current = true
    fetchFollowList(actorId, direction, query, 1, FOLLOW_PAGE_SIZE)
      .then((res) => {
        if (!alive) return
        itemsRef.current = res.content
        pageRef.current = res.page
        lastRef.current = res.last
        setItems(res.content)
        setLast(res.last)
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
  }, [actorId, direction, query, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchFollowList(actorId, direction, query, pageRef.current + 1, FOLLOW_PAGE_SIZE)
      itemsRef.current = [...itemsRef.current, ...res.content]
      pageRef.current = res.page
      lastRef.current = res.last
      setItems(itemsRef.current)
      setLast(res.last)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [actorId, direction, query])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, {
    root: rootRef,
    rootMargin: '250px',
    enabled: canLoadMore,
  })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const retry = () => {
    setListState('loading')
    setLoadMoreError(false)
    setReloadKey((key) => key + 1)
  }

  const emptyLabel = query
    ? t('common.noResults')
    : t(direction === 'followers' ? 'profile.follows.emptyFollowers' : 'profile.follows.emptyFollowing')

  return (
    <div ref={rootRef} className="follow-list scroll-container">
      {listState === 'loading' && <p className="follow-list-state">{t('common.loading')}</p>}

      {listState === 'error' && (
        <div className="follow-list-state">
          <p>{t('errors.serverError')}</p>
          <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
            {t('jobs.results.retry')}
          </Button>
        </div>
      )}

      {listState === 'ready' && items.length === 0 && (
        <p className="follow-list-state">{emptyLabel}</p>
      )}

      {listState === 'ready' && items.length > 0 && (
        <>
          <div className="follow-list-rows">
            {items.map((actor) => (
              <ActorRow
                key={actor.id}
                actor={actor}
                followedByViewer={actor.isFollowedByViewer}
                variant={ActorRowVariant.PLAIN}
                onFollowChange={onFollowChange}
              />
            ))}
          </div>

          <div ref={sentinelRef} className="follow-list-sentinel" aria-hidden />

          {loadingMore && <p className="follow-list-more">{t('common.loading')}</p>}

          {loadMoreError && (
            <div className="follow-list-more follow-list-more-error">
              <span>{t('jobs.results.loadMoreError')}</span>
              <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
                {t('jobs.results.retry')}
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

export default FollowList
