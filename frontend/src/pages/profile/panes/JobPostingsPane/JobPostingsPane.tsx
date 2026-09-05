import './JobPostingsPane.scss'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Dropdown } from '@shared/components'
import { FilterCapsules } from '@pages/profile/components/FilterCapsules'
import type { ActorJobPostingsQuery, JobPostingsSort, SavedJobItemDto } from '@utils/api/jobs'
import type { PagedResponse } from '@utils/api/types'
import { jobPostingsCacheKey } from '@pages/profile/lib/jobPostingsCache'
import JobPostingsList from './JobPostingsList'
import { sortOptions, statusCapsules, type StatusFilter } from './jobPostingsOptions'

interface JobPostingsPaneProps {
  actorId: string
  /** Server-computed; a role check on the client would be a second copy of it. */
  canManage: boolean
  /** The first page the profile page already fetched. */
  initialPage: PagedResponse<SavedJobItemDto>
}

const JobPostingsPane = ({ actorId, canManage, initialPage }: JobPostingsPaneProps) => {
  const { t } = useTranslation()
  const [status, setStatus] = useState<StatusFilter>('all')
  const [sort, setSort] = useState<JobPostingsSort | ''>('')

  const query = useMemo<ActorJobPostingsQuery | undefined>(() => {
    if (!canManage) return undefined
    return {
      ...(status === 'all' ? {} : { status }),
      ...(sort === '' ? {} : { sort }),
    }
  }, [canManage, status, sort])

  const seed = useMemo(
    () => ({ items: initialPage.content, page: initialPage.page, last: initialPage.last }),
    [initialPage],
  )

  const isDefaultView = status === 'all' && sort === ''
  const cacheKey = canManage
    ? jobPostingsCacheKey(actorId, status, sort)
    : jobPostingsCacheKey(actorId, '', '')

  return (
    <div className="job-postings-pane">
      {canManage && (
        <div className="job-postings-pane-controls">
          <FilterCapsules
            options={statusCapsules(t)}
            active={status}
            onChange={(key) => setStatus(key as StatusFilter)}
            ariaLabel={t('profile.tabs.jobPostings')}
          />

          <span className="job-postings-pane-divider" aria-hidden />

          <div className="job-postings-pane-sort">
            <Dropdown
              label={t('profile.postings.sortLabel')}
              options={sortOptions(t)}
              value={sort}
              onChange={(next) => setSort(typeof next === 'string' ? (next as JobPostingsSort | '') : '')}
            />
          </div>
        </div>
      )}

      <JobPostingsList
        key={cacheKey}
        actorId={actorId}
        cacheKey={cacheKey}
        query={query}
        seed={isDefaultView ? seed : undefined}
      />
    </div>
  )
}

export default JobPostingsPane
