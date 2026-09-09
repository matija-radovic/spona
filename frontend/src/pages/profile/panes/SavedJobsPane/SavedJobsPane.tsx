import './SavedJobsPane.scss'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconHeart, IconHeartFilled } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components/form/Button'
import { JobCard } from '@pages/jobs/components/JobCard'
import { FilterCapsules, type CapsuleOption } from '@pages/profile/components/FilterCapsules'
import { fetchJobsByIds, type SavedJobItemDto } from '@utils/api/jobs'
import { getSavedJobs, toggleSavedJob } from '@utils/savedJobs'
import {
  clearSavedJobsCache,
  getSavedJobsCache,
  sameIdSet,
  setSavedJobsCache,
} from '@pages/profile/lib/savedJobsCache'

type SavedFilter = 'active' | 'finished' | 'all'
type ListState = 'loading' | 'error' | 'ready'

const mergeById = (base: SavedJobItemDto[], extra: SavedJobItemDto[]): SavedJobItemDto[] => {
  const seen = new Set(base.map((job) => job.id))
  return [...base, ...extra.filter((job) => !seen.has(job.id))]
}

/**
 * Own-profile Saved-jobs pane. localStorage is the source of truth (§10). The
 * initial fetch hydrates OPEN only; Finished/All lazily hydrate CLOSED once, all
 * into a single array filtered in code. A module cache survives tab switches and
 * is invalidated when the saved set changed.
 */
const SavedJobsPane = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const savedIds = useMemo(() => getSavedJobs().map((entry) => entry.id), [])

  // Lazy-init from the cache when the saved set is unchanged, so a tab round-trip
  // needs no synchronous effect setState (the JobsView discipline).
  const initialCache = useMemo(() => {
    const cached = getSavedJobsCache()
    return cached && sameIdSet(cached.ids, savedIds) ? cached : null
  }, [savedIds])

  const [active, setActive] = useState<SavedFilter>('active')
  const [items, setItems] = useState<SavedJobItemDto[]>(initialCache?.items ?? [])
  const [state, setState] = useState<ListState>(
    savedIds.length === 0 || initialCache ? 'ready' : 'loading',
  )
  const [loadingClosed, setLoadingClosed] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [unsaved, setUnsaved] = useState<ReadonlySet<string>>(() => new Set())

  const itemsRef = useRef<SavedJobItemDto[]>(initialCache?.items ?? [])
  const hydratedClosedRef = useRef(initialCache?.hydratedClosed ?? false)
  const loadingClosedRef = useRef(false)

  const writeCache = useCallback((next: SavedJobItemDto[]) => {
    const ids = getSavedJobs().map((entry) => entry.id)
    setSavedJobsCache({
      ids,
      items: next.filter((job) => ids.includes(job.id)),
      hydratedOpen: true,
      hydratedClosed: hydratedClosedRef.current,
    })
  }, [])

  // Initial hydrate (OPEN only) - only fetches on a cache miss; a cache hit was
  // already lazy-initialised above, so this effect setStates only in callbacks.
  useEffect(() => {
    if (savedIds.length === 0) return

    const cached = getSavedJobsCache()
    if (cached && sameIdSet(cached.ids, savedIds)) return

    clearSavedJobsCache()
    hydratedClosedRef.current = false
    let alive = true
    fetchJobsByIds(savedIds, ['OPEN'])
      .then((res) => {
        if (!alive) return
        itemsRef.current = res
        setItems(res)
        writeCache(res)
        setState('ready')
      })
      .catch(() => {
        if (alive) setState('error')
      })
    return () => {
      alive = false
    }
  }, [savedIds, reloadKey, writeCache])

  // Lazy one-time CLOSED hydrate, triggered the first time Finished/All is opened.
  const ensureClosed = useCallback(async () => {
    if (hydratedClosedRef.current || loadingClosedRef.current) return
    loadingClosedRef.current = true
    setLoadingClosed(true)
    try {
      const closed = await fetchJobsByIds(savedIds, ['CLOSED'])
      const merged = mergeById(itemsRef.current, closed)
      hydratedClosedRef.current = true
      itemsRef.current = merged
      setItems(merged)
      writeCache(merged)
    } catch {
      /* leave hydratedClosed false so a later switch retries */
    } finally {
      loadingClosedRef.current = false
      setLoadingClosed(false)
    }
  }, [savedIds, writeCache])

  const onCapsule = (key: string) => {
    const next = key as SavedFilter
    setActive(next)
    if ((next === 'finished' || next === 'all') && !hydratedClosedRef.current) ensureClosed()
  }

  const retry = () => {
    setState('loading')
    setReloadKey((k) => k + 1)
  }

  const onToggleSaved = (job: SavedJobItemDto) => {
    const nowSaved = toggleSavedJob({ id: job.id, slug: job.slug })
    setUnsaved((prev) => {
      const next = new Set(prev)
      if (nowSaved) next.delete(job.id)
      else next.add(job.id)
      return next
    })
    writeCache(itemsRef.current)
  }

  const visible = items.filter((job) =>
    active === 'all' ? true : active === 'active' ? job.status === 'OPEN' : job.status !== 'OPEN',
  )

  const capsules: CapsuleOption[] = [
    { key: 'active', label: t('profile.saved.filter.active') },
    { key: 'finished', label: t('profile.saved.filter.finished') },
    { key: 'all', label: t('profile.saved.filter.all') },
  ]

  if (savedIds.length === 0) {
    return <p className="saved-jobs-pane-empty">{t('profile.saved.empty')}</p>
  }

  if (state === 'loading') {
    return <p className="saved-jobs-pane-state">{t('common.loading')}</p>
  }

  if (state === 'error') {
    return (
      <div className="saved-jobs-pane-state">
        <p>{t('errors.serverError')}</p>
        <Button variant={ButtonVariant.TERTIARY} onClick={retry}>
          {t('jobs.results.retry')}
        </Button>
      </div>
    )
  }

  return (
    <div className="saved-jobs-pane">
      <FilterCapsules
        options={capsules}
        active={active}
        onChange={onCapsule}
        ariaLabel={t('profile.tabs.savedJobs')}
      />

      {loadingClosed && visible.length === 0 ? (
        <p className="saved-jobs-pane-state">{t('common.loading')}</p>
      ) : visible.length === 0 ? (
        <p className="saved-jobs-pane-empty">{t('profile.saved.emptyFiltered')}</p>
      ) : (
        <ul className="saved-jobs-pane-list">
          {visible.map((job) => {
            const saved = !unsaved.has(job.id)
            return (
              <li key={job.id}>
                <JobCard
                  job={job}
                  isActive={false}
                  onSelect={(slug) => navigate(`/jobs/${slug}`)}
                />
                <button
                  type="button"
                  className={`saved-jobs-pane-fav${saved ? ' is-active' : ''}`}
                  onClick={() => onToggleSaved(job)}
                  aria-pressed={saved}
                  aria-label={t(saved ? 'jobs.favorite.remove' : 'jobs.favorite.add')}
                >
                  {saved ? <IconHeartFilled /> : <IconHeart />}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default SavedJobsPane
