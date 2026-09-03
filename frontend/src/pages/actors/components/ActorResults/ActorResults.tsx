import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import useOnScreen from '@shared/hooks/useOnScreen'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { searchActors, type ActorSearchItemDto } from '@utils/api/profiles'
import { ActorSearchRow } from '../ActorSearchRow'

const PAGE_SIZE = 20

type ListState = 'loading' | 'error' | 'ready'

interface ActorResultsProps {
  /** Non-empty debounced query. */
  query: string
  type: 'org' | 'user'
}

/**
 * The result list for one (query, type). Keyed by the parent so a new query
 * remounts it - the first-page effect then setStates only in async callbacks
 * (the JobsView discipline), scrolling against the page (viewport) scroller.
 */
const ActorResults = ({ query, type }: ActorResultsProps) => {
  const { t } = useTranslation()

  const [items, setItems] = useState<ActorSearchItemDto[]>([])
  const [listState, setListState] = useState<ListState>('loading')
  const [last, setLast] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)

  const itemsRef = useRef<ActorSearchItemDto[]>([])
  const pageRef = useRef(1)
  const lastRef = useRef(false)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let alive = true
    loadingRef.current = true
    searchActors(query, type, 1, PAGE_SIZE)
      .then((res) => {
        if (!alive) return
        itemsRef.current = res.content
        pageRef.current = 1
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
  }, [query, type])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await searchActors(query, type, pageRef.current + 1, PAGE_SIZE)
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
  }, [query, type])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  if (listState === 'loading') {
    return <p className="actor-search-state">{t('common.loading')}</p>
  }

  if (listState === 'error') {
    return (
      <div className="actor-search-state">
        <p>{t('errors.serverError')}</p>
      </div>
    )
  }

  if (items.length === 0) {
    return <p className="actor-search-state">{t('actors.search.empty')}</p>
  }

  return (
    <>
      <div className="actor-search-results">
        {items.map((item) => (
          <ActorSearchRow key={`${item.actorType}-${item.handle}`} item={item} />
        ))}
      </div>

      <div ref={sentinelRef} className="actor-search-sentinel" aria-hidden />

      {loadingMore && <p className="actor-search-more">{t('common.loading')}</p>}

      {loadMoreError && (
        <div className="actor-search-more actor-search-more-error">
          <span>{t('jobs.results.loadMoreError')}</span>
          <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
            {t('jobs.results.retry')}
          </Button>
        </div>
      )}
    </>
  )
}

export default ActorResults
