import { useTranslation } from 'react-i18next'
import { IconClick } from '@tabler/icons-react'

const JobsHint = () => {
  const { t } = useTranslation()
  return (
    <div className="jobs-hint">
      <IconClick aria-hidden />
      <p>{t('jobs.panel.hint')}</p>
    </div>
  )
}

export default JobsHint
