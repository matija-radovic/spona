import './JobCard.scss'
import { useTranslation } from 'react-i18next'
import { IconBuilding, IconMapPin, IconCoin } from '@tabler/icons-react'
import useFormat from '@shared/hooks/useFormat'
import type { JobListItemDto, JobStatus } from '@utils/api/jobs'
import { formatSalary } from '@pages/jobs/lib/formatSalary'

/** What fits beside the logo tile at the list column's narrow end. */
const KEYWORDS_SHOWN = 3

interface JobCardProps {
  /** Only an owner's postings rows carry a status, so only they get a capsule. */
  job: JobListItemDto & { status?: JobStatus }
  isActive: boolean
  onSelect: (slug: string) => void
}

const JobCard = ({ job, isActive, onSelect }: JobCardProps) => {
  const { t } = useTranslation()
  const format = useFormat()
  const { createdFor, keywords } = job
  const isOrg = createdFor.actorType === 'org'
  const shown = keywords?.slice(0, KEYWORDS_SHOWN) ?? []
  const hidden = (keywords?.length ?? 0) - shown.length

  return (
    <button
      type="button"
      className={`job-card${isActive ? ' is-active' : ''}`}
      onClick={() => onSelect(job.slug)}
      aria-pressed={isActive}
    >
      <div className="job-card-body">
        <div className="job-card-title-row">
          <h3 className="job-card-title">{job.title}</h3>
          {job.status && job.status !== 'OPEN' && (
            <span className={`job-card-status is-${job.status.toLowerCase()}`}>
              {t(`jobs.status.${job.status}`)}
            </span>
          )}
        </div>

        <span className="job-card-poster">
          {isOrg ? createdFor.name : t('jobs.card.privatePoster')}
        </span>

        <span className="job-card-meta">
          <IconMapPin aria-hidden />
          {job.location}
        </span>

        {job.salary && (
          <span className="job-card-meta job-card-salary">
            <IconCoin aria-hidden />
            {formatSalary(job.salary, t, format.locale)}
          </span>
        )}

        {shown.length > 0 && (
          <div className="job-card-keywords">
            {shown.map((keyword) => (
              <span key={keyword} className="job-card-keyword">
                {keyword}
              </span>
            ))}
            {hidden > 0 && (
              <span className="job-card-keyword job-card-keyword-more">
                {t('jobs.card.keywordsMore', { count: hidden })}
              </span>
            )}
          </div>
        )}
      </div>

      {isOrg && (
        <span className="job-card-logo">
          {createdFor.avatarUrl ? (
            <img src={createdFor.avatarUrl} alt={t('jobs.card.companyLogoAlt')} />
          ) : (
            <IconBuilding aria-hidden />
          )}
        </span>
      )}
    </button>
  )
}

export default JobCard
