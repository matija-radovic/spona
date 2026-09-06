import './JobManage.scss'
import { useCallback, useEffect, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SectionLayout } from '@shared/components'
import { fetchJobBySlug, type JobDetailDto } from '@utils/api/jobs'
import { parseProblem, type ProblemDetail } from '@utils/api/types'
import JobManageHeader from './JobManageHeader'
import { JOB_MANAGE_SECTIONS } from './sections'
import type { JobManageOutletContext } from './jobManageContext'

const JobManage = () => {
  const { t } = useTranslation()
  const { slug } = useParams()

  const [job, setJob] = useState<JobDetailDto | null>(null)
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [loadedSlug, setLoadedSlug] = useState<string | undefined>(undefined)

  const load = useCallback((): Promise<void> => {
    if (!slug) return Promise.resolve()
    return fetchJobBySlug(slug)
      .then((dto) => {
        setJob(dto)
        setProblem(null)
      })
      .catch((error: unknown) => {
        setJob(null)
        setProblem(parseProblem(error))
      })
      .finally(() => setLoadedSlug(slug))
  }, [slug])

  useEffect(() => {
    void load()
  }, [load])

  if (loadedSlug !== slug) {
    return (
      <div className="job-manage container">
        <p className="job-manage-status">{t('common.loading')}</p>
      </div>
    )
  }

  if (problem || !job) {
    const isNotFound = problem?.status === 404
    return (
      <div className="job-manage container">
        <p className="job-manage-status">
          {isNotFound ? t('jobs.panel.notFound') : problem?.detail || t('errors.serverError')}
        </p>
      </div>
    )
  }

  if (!job.viewerCanEdit) {
    return <Navigate to={`/jobs/${slug}`} replace />
  }

  const context: JobManageOutletContext = { job, refetch: load }

  return (
    <div className="job-manage container">
      <JobManageHeader job={job} refetch={load} />

      <SectionLayout
        sections={JOB_MANAGE_SECTIONS}
        context={context}
        navLabel={t('jobs.manage.nav.label')}
      />
    </div>
  )
}

export default JobManage
