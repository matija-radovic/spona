import './JobStatusChip.scss'
import { useTranslation } from 'react-i18next'
import type { JobStatus } from '@utils/api/jobs'

interface JobStatusChipProps {
  status: JobStatus
  /** Whether or not to render the capsule when job status is Active */
  showOpen?: boolean
  className?: string
}

const JobStatusChip = ({ status, showOpen = false, className }: JobStatusChipProps) => {
  const { t } = useTranslation()

  if (status === 'OPEN' && !showOpen) return null

  return (
    <span
      className={['job-status-chip', `is-${status.toLowerCase()}`, className]
        .filter(Boolean)
        .join(' ')}
    >
      {t(`jobs.status.${status}`)}
    </span>
  )
}

export default JobStatusChip
