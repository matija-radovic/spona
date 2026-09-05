import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import useOnScreen from '@shared/hooks/useOnScreen'
import { JobCard } from '@pages/jobs/components/JobCard'
import {
  fetchActorJobPostings,
  type ActorJobPostingsQuery,
  type SavedJobItemDto,
} from '@utils/api/jobs'
import {
  getJobPostingsCache,
  setJobPostingsCache,
  type JobPostingsCacheEntry,
} from '@pages/profile/lib/jobPostingsCache'

const PAGE_SIZE = 10

type ListState = 'loading' | 'error' | 'ready'

interface JobPostingsListProps {
  actorId: string
  cacheKey: string
  /** Absent for the public shape: both parameters are owner-only on the wire. */
  query?: ActorJobPostingsQuery
  /** The page the profile already fetched, for the view it was fetched for. */
  seed?: JobPostingsCacheEntry
}

/** The list engine for one (actorId, status, sort), keyed by the parent so a
 *  control flip remounts it against the seed or the module cache. */
const JobPostingsList = ({ actorId, cacheKey, query, seed }: JobPostingsListProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const initial = useMemo(() => getJobPostingsCache(cacheKey) ?? seed, [cacheKey, seed])

  const [items, setItems] = useState<SavedJobItemDto[]>(initial?.items ?? [])
  const [last, setLast] = useState(initial?.last ?? false)
  const [state, setState] = useState<ListState>(initial ? 'ready' : 'loading')
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadMoreError, setLoadMoreError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const itemsRef = useRef<SavedJobItemDto[]>(initial?.items ?? [])
  const pageRef = useRef(initial?.page ?? 1)
  const lastRef = useRef(initial?.last ?? false)
  const loadingRef = useRef(false)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const applyPage = useCallback(
    (res: Awaited<ReturnType<typeof fetchActorJobPostings>>, append: boolean) => {
      const merged = append ? [...itemsRef.current, ...res.content] : res.content
      itemsRef.current = merged
      pageRef.current = res.page
      lastRef.current = res.last
      setItems(merged)
      setLast(res.last)
      setJobPostingsCache(cacheKey, { items: merged, page: res.page, last: res.last })
    },
    [cacheKey],
  )

  useEffect(() => {
    if (initial) return

    let alive = true
    loadingRef.current = true
    fetchActorJobPostings(actorId, query, 1, PAGE_SIZE)
      .then((res) => {
        if (!alive) return
        applyPage(res, false)
        setState('ready')
      })
      .catch(() => {
        if (alive) setState('error')
      })
      .finally(() => {
        loadingRef.current = false
      })
    return () => {
      alive = false
    }
  }, [actorId, query, initial, applyPage, reloadKey])

  const loadMore = useCallback(async () => {
    if (loadingRef.current || lastRef.current) return
    loadingRef.current = true
    setLoadingMore(true)
    setLoadMoreError(false)
    try {
      const res = await fetchActorJobPostings(actorId, query, pageRef.current + 1, PAGE_SIZE)
      applyPage(res, true)
    } catch {
      setLoadMoreError(true)
    } finally {
      setLoadingMore(false)
      loadingRef.current = false
    }
  }, [actorId, query, applyPage])

  const canLoadMore = state === 'ready' && !last && !loadMoreError && items.length > 0
  const sentinelVisible = useOnScreen(sentinelRef, { rootMargin: '250px', enabled: canLoadMore })

  useEffect(() => {
    if (sentinelVisible && canLoadMore && !loadingRef.current) loadMore()
  }, [sentinelVisible, canLoadMore, loadMore])

  const retry = () => {
    setState('loading')
    setLoadMoreError(false)
    setReloadKey((k) => k + 1)
  }

  if (state === 'loading') {
    return <p className="job-postings-pane-state">{t('common.loading')}</p>
  }

  if (state === 'error') {
    return (
      <div className="job-postings-pane-state">
        <p>{t('errors.serverError')}</p>
        <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
          {t('jobs.results.retry')}
        </Button>
      </div>
    )
  }

  if (items.length === 0) {
    return <p className="job-postings-pane-empty">{t('profile.postings.emptyFiltered')}</p>
  }

  return (
    <div className="job-postings-pane-body">
      <ul className="job-postings-pane-list">
        {items.map((job) => (
          <li key={job.id}>
            <JobCard job={job} isActive={false} onSelect={(slug) => navigate(`/jobs/${slug}`)} />
          </li>
        ))}
      </ul>

      <div ref={sentinelRef} className="job-postings-pane-sentinel" aria-hidden />

      {loadingMore && <p className="job-postings-pane-more">{t('common.loading')}</p>}

      {loadMoreError && (
        <div className="job-postings-pane-more job-postings-pane-more-error">
          <span>{t('jobs.results.loadMoreError')}</span>
          <Button variant={ButtonVariant.TERTIARY} onClick={loadMore}>
            {t('jobs.results.retry')}
          </Button>
        </div>
      )}
    </div>
  )
}

export default JobPostingsList
