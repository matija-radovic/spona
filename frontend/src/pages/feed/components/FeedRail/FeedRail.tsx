import './FeedRail.scss'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { RailLegal } from '@shared/components'
import { fetchJobs, NO_JOB_FILTERS, type JobListItemDto, type JobPosterDto } from '@utils/api/jobs'

const RAIL_JOBS = 3

const publisherOf = (createdFor: JobPosterDto, privateLabel: string): string =>
  createdFor.actorType === 'org' ? createdFor.name : privateLabel

/** The newest postings plus the legal block */
const FeedRail = () => {
  const { t } = useTranslation()
  const [jobs, setJobs] = useState<JobListItemDto[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    fetchJobs(NO_JOB_FILTERS, 1, RAIL_JOBS)
      .then((res) => {
        if (alive) setJobs(res.content)
      })
      .catch(() => {
        if (alive) setFailed(true)
      })
    return () => {
      alive = false
    }
  }, [])

  const showJobs = !failed && (jobs === null || jobs.length > 0)

  return (
    <aside className="feed-rail">
      <div className="feed-rail-sticky">
        {showJobs && (
          <section className="feed-rail-card">
            <h2 className="feed-rail-title">{t('feed.rail.jobsTitle')}</h2>

            <ul className="feed-rail-jobs">
              {jobs?.map((job) => (
                <li key={job.id}>
                  <Link to={`/jobs/${job.slug}`} className="feed-rail-job">
                    <span className="feed-rail-job-title">{job.title}</span>
                    <span className="feed-rail-job-meta">
                      {publisherOf(job.createdFor, t('jobs.card.privatePoster'))} · {job.location}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>

            <Link to="/jobs" className="feed-rail-all">
              {t('feed.rail.allJobs')}
            </Link>
          </section>
        )}

        <RailLegal />
      </div>
    </aside>
  )
}

export default FeedRail
