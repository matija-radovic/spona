import './JobPostingsPane.scss'
import { useNavigate } from 'react-router-dom'
import { JobCard } from '@pages/jobs/components/JobCard'
import type { JobListItemDto } from '@utils/api/jobs'

interface JobPostingsPaneProps {
  /** The actor's public OPEN postings, fetched eagerly by Profile (§9). The tab
   *  only renders when this is non-empty, so no empty state is needed here. */
  postings: JobListItemDto[]
}

/**
 * The actor's public job postings as JobCard rows. Deep-links to /jobs/:slug
 * (which already renders the job full-width in direct mode). No filters, no
 * pagination - and no privacy UI: visibility is enforced entirely backend-side.
 */
const JobPostingsPane = ({ postings }: JobPostingsPaneProps) => {
  const navigate = useNavigate()

  return (
    <ul className="job-postings-pane">
      {postings.map((job) => (
        <li key={job.id}>
          <JobCard job={job} isActive={false} onSelect={(slug) => navigate(`/jobs/${slug}`)} />
        </li>
      ))}
    </ul>
  )
}

export default JobPostingsPane
