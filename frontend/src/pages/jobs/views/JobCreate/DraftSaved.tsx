import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { IconCheck } from '@tabler/icons-react'
import { Button, ButtonVariant } from '@shared/components'

interface DraftSavedProps {
  slug: string
}

const DraftSaved = ({ slug }: DraftSavedProps) => {
  const { t } = useTranslation()

  return (
    <div className="job-create-saved">
      <span className="job-create-saved-mark" aria-hidden>
        <IconCheck />
      </span>

      <h2 className="job-create-saved-title">{t('jobs.create.draftSaved')}</h2>
      <p className="job-create-saved-body">{t('jobs.create.draftSavedBody')}</p>

      <Link to={`/jobs/${slug}`} className="job-create-saved-slug">
        /jobs/{slug}
      </Link>

      <div className="job-create-saved-actions">
        <Button variant={ButtonVariant.SECONDARY} to="/jobs">
          {t('jobs.title')}
        </Button>
        <Button variant={ButtonVariant.PRIMARY} to={`/jobs/${slug}`}>
          {t('jobs.create.viewPosting')}
        </Button>
      </div>
    </div>
  )
}

export default DraftSaved
