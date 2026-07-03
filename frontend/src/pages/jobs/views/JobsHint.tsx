import { useTranslation } from 'react-i18next'
import { IconClick } from '@tabler/icons-react'

/** Right-panel placeholder shown at /jobs (index) — prompts list interaction. */
const JobsHint = () => {
  const { t } = useTranslation()
  return (
    <div className="jobs-hint">
      <IconClick size={42} stroke={1.5} aria-hidden />
      <p>{t('jobs.panel.hint')}</p>
    </div>
  )
}

export default JobsHint
