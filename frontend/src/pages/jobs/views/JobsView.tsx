import './JobsView.scss'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { MQ } from '@shared/css/mediaqueries'
import useGhostScrollbar from '@shared/hooks/useGhostScrollbar'
import useMediaQuery from '@shared/hooks/useMediaQuery'
import useOnScreen from '@shared/hooks/useOnScreen'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { fetchJobs, type JobListItemDto } from '@utils/api/jobs'
import { filtersToSearchParams, type FilterState } from '@pages/jobs/lib/filters'
import { getJobsCache, saveJobsScroll, setJobsCache } from '@pages/jobs/lib/jobsCache'
import type { JobsOutletContext } from '@pages/jobs/lib/jobsOutletContext'
import { SearchFilterBar } from '@pages/jobs/components/SearchFilterBar'
import { JobCard } from '@pages/jobs/components/JobCard'

const PAGE_SIZE = 20

type ListState = 'loading' | 'error' | 'ready'

interface JobsViewProps {
  filters: FilterState
  /** Serialised filter params - cache key. Fixed for this instance (parent keys us). */
  queryKey: string
  /** Direct deep-link mode: render only the job full-width, never fetch the list. */
  detailOnly: boolean
}

/**
 * List/bar owner. The parent (`JobsLayout`) keys this by `queryKey::detailOnly`,
 * so a new search or a mode change remounts it with a clean slate - which lets
 * the list state lazy-initialise from the cache and keeps the fetch effect's
 * setState confined to async callbacks. Across index <-> :slug (same key) it
 * stays mounted: no refetch, scroll preserved.
 */
const JobsView = ({ filters, queryKey, detailOnly }: JobsViewProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const { slug } = useParams()

  const initialCache = useMemo(() => getJobsCache(queryKey), [queryKey])

  const isMobile = useMediaQuery(MQ.phone)

  // -- List state (lazy-initialised from cache; refs shadow it for async logic) --
  const [items, setItems] = useState<JobListItemDto[]>(initialCache?.items ?? [])
  const [totalElements, setTotalElements] = useState(initialCache?.totalElements ?? 0)
  const [last, setLast] = useState(initialCache?.last ?? false)
  const [listState, setListState] = useState<ListState>(detailOnly || initialCache ? 'ready' : 'loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const itemsRef = useRef<JobListItemDto[]>(initialCache?.items ?? [])
  const pageRef = useRef(initialCache?.page ?? 1)
  const lastRef = useRef(initialCache?.last ?? false)
  const loadingRef = useRef(false)

  const listRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const sentinelRef = useRef<HTMLDivElement>(null)
  useGhostScrollbar(listRef)
  useGhostScrollbar(panelRef)

  const applyPage = useCallback(
    (res: Awaited<ReturnType<typeof fetchJobs>>, append: boolean) => {
      const merged = append ? [...itemsRef.current, ...res.content] : res.content
      itemsRef.current = merged
      pageRef.current = res.page
      lastRef.current = res.last
      setItems(merged)
      setTotalElements(res.totalElements)
      setLast(res.last)
      setJobsCache(queryKey, {
        items: merged,
        page: res.page,
        last: res.last,
        totalElements: res.totalElements,
        scrollTop: listRef.current?.scrollTop ?? 0,
      })
    },
    [queryKey],
  )

  // First page: fetch on a cache miss, or just restore scroll on a cache hit.
  // All setState here lives in async callbacks (no synchronous effect setState).
  useEffect(() => {
    if (detailOnly) return

    const cached = getJobsCache(queryKey)
    if (cached) {
      requestAnimationFrame(() => {
        if (listRef.current) listRef.current.scrollTop = cached.scrollTop
      })
      return
    }

    let active = true
    loadingRef.current = true
    fetchJobs(filters, 1, PAGE_SIZE)
      .then((res) => {
        if (!active) return
        applyPage(res, false)
        setListState('ready')
      })
      .catch(() => {
        if (active) setListState('error')
      })
      .finally(() => {
        loadingRef.current = false
      })
    return () => {
      active = false
    }
  }, [detailOnly, queryKey, filters, applyPage, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchJobs(filters, pageRef.current + 1, PAGE_SIZE)
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [applyPage, filters])

  const canLoadMore = listState === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, {
    root: listRef,
    rootMargin: '250px',
    enabled: canLoadMore && !detailOnly,
  })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const retryFirstPage = () => {
    setListState('loading')
    setLoadMoreError(false)
    setReloadKey((k) => k + 1)
  }

  // -- Navigation ----------------------------------------------------------------
  const onSearch = useCallback(
    (next: FilterState) => {
      const params = filtersToSearchParams(next).toString()
      navigate({ pathname: '/jobs', search: params ? `?${params}` : '' })
    },
    [navigate],
  )

  const onSelect = useCallback(
    (nextSlug: string) => {
      navigate({ pathname: `/jobs/${nextSlug}`, search: location.search }, { state: { fromList: true } })
    },
    [navigate, location.search],
  )

  const onBack = useCallback(() => {
    navigate({ pathname: '/jobs', search: location.search })
  }, [navigate, location.search])

  const outletContext: JobsOutletContext = { isMobile, detailOnly, activeSlug: slug, onBack }

  const bodyClass = ['jobs-body', detailOnly ? 'is-detail-only' : '', slug && !detailOnly ? 'is-detail' : '']
    .filter(Boolean)
    .join(' ')

  return (
    <div className="jobs">
      <div className="jobs-inner container">
        <SearchFilterBar filters={filters} onSearch={onSearch} isMobile={isMobile} />

        <div className={bodyClass}>
          {!detailOnly && (
            <div
              ref={listRef}
              className="jobs-list scroll-container"
              onScroll={(e) => saveJobsScroll(queryKey, e.currentTarget.scrollTop)}
            >
              {listState === 'loading' && items.length === 0 && (
                <div className="jobs-list-state">{t('common.loading')}</div>
              )}

              {listState === 'error' && items.length === 0 && (
                <div className="jobs-list-state">
                  <p>{t('errors.serverError')}</p>
                  <Button variant={ButtonVariant.TERTIARY} onClick={retryFirstPage}>
                    {t('jobs.results.retry')}
                  </Button>
                </div>
              )}

              {listState === 'ready' && totalElements === 0 && (
                <div className="jobs-list-empty">
                  <p className="jobs-list-empty-title">{t('jobs.results.empty')}</p>
                  <p className="jobs-list-empty-hint">{t('jobs.results.emptyHint')}</p>
                </div>
              )}

              {items.length > 0 && (
                <>
                  <ul className="jobs-list-items">
                    {items.map((job) => (
                      <li key={job.id}>
                        <JobCard job={job} isActive={job.slug === slug} onSelect={onSelect} />
                      </li>
                    ))}
                  </ul>

                  <div ref={sentinelRef} className="jobs-list-sentinel" aria-hidden />

                  {loadingMore && <div className="jobs-list-more">{t('common.loading')}</div>}

                  {loadMoreError && (
                    <div className="jobs-list-more jobs-list-more-error">
                      <span>{t('jobs.results.loadMoreError')}</span>
                      <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
                        {t('jobs.results.retry')}
                      </Button>
                    </div>
                  )}

                  {last && !loadMoreError && (
                    <div className="jobs-list-footer">
                      {t('jobs.results.count', { count: totalElements })}
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          <div ref={panelRef} className="jobs-panel scroll-container">
            <Outlet context={outletContext} />
          </div>
        </div>
      </div>
    </div>
  )
}

export default JobsView
