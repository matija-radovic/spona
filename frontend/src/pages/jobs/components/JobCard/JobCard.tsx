import './JobCard.scss'
import { useTranslation } from 'react-i18next'
import { IconBuilding, IconMapPin, IconCoin } from '@tabler/icons-react'
import type { JobListItemDto } from '@utils/api/jobs'
import { formatSalary } from '@pages/jobs/lib/formatSalary'

interface JobCardProps {
  job: JobListItemDto
  isActive: boolean
  onSelect: (slug: string) => void
}

const JobCard = ({ job, isActive, onSelect }: JobCardProps) => {
  const { t } = useTranslation()
  const { createdFor } = job
  const isOrg = createdFor.actorType === 'org'

  return (
    <button
      type="button"
      className={`job-card${isActive ? ' is-active' : ''}`}
      onClick={() => onSelect(job.slug)}
      aria-pressed={isActive}
    >
      <div className="job-card-body">
        <h3 className="job-card-title">{job.title}</h3>

        <span className="job-card-poster">
          {isOrg ? createdFor.displayName : t('jobs.card.privatePoster')}
        </span>

        {job.location && (
          <span className="job-card-meta">
            <IconMapPin aria-hidden />
            {job.location}
          </span>
        )}

        {job.salary && (
          <span className="job-card-meta job-card-salary">
            <IconCoin aria-hidden />
            {formatSalary(job.salary, t)}
          </span>
        )}

        {/* TODO: keywords row (needs backend) */}
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
