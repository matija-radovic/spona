import { useMemo } from 'react'
import { useLocation, useParams, useSearchParams } from 'react-router-dom'
import { filtersToSearchParams, searchParamsToFilters } from '@pages/jobs/lib/filters'
import { hasJobsCache } from '@pages/jobs/lib/jobsCache'
import JobsView from './JobsView'

/**
 * Route element for `/jobs`. Thin on purpose: it derives the filter state + the
 * render mode from the URL, then keys `JobsView` by `queryKey::detailOnly` so a
 * new search (or a direct-vs-in-app mode change) remounts the view with a clean
 * slate, while index <-> :slug (same key) keeps it mounted.
 */
const JobsLayout = () => {
  const location = useLocation()
  const { slug } = useParams()
  const [searchParams] = useSearchParams()

  const filters = useMemo(() => searchParamsToFilters(searchParams), [searchParams])
  const queryKey = useMemo(() => filtersToSearchParams(filters).toString(), [filters])

  // Direct/typed/refreshed entry at /jobs/:slug (no `fromList` state, empty cache)
  // renders only the job full-width and never fetches the list.
  const fromList = (location.state as { fromList?: boolean } | null)?.fromList === true
  const detailOnly = !!slug && !fromList && !hasJobsCache(queryKey)

  return <JobsView key={`${queryKey}::${detailOnly}`} filters={filters} queryKey={queryKey} detailOnly={detailOnly} />
}

export default JobsLayout
