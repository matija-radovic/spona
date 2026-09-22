import './PrivacySection.scss'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { IconCheck } from '@tabler/icons-react'
import {
  Button,
  ButtonVariant,
  SaveBar,
  SectionPanel,
  UnsavedChangesModal,
} from '@shared/components'
import useUnsavedGuard from '@shared/hooks/useUnsavedGuard'
import { parseProblem } from '@utils/api/types'
import { problemMessage } from '@utils/api/problemMessage'
import { updatePrivacy, type PrivacyDto } from '@utils/api/settings'
import { canEditOrgProfile } from '../../../lib/permissions'
import { useSettingsContext } from '../../../lib/settingsOutletContext'

const PrivacySection = () => {
  const { t } = useTranslation()
  const context = useSettingsContext()

  const canEdit = context.kind === 'user' || canEditOrgProfile(context.settings.viewerRole)

  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    getValues,
    formState: { isDirty, isSubmitting },
  } = useForm<PrivacyDto>({ defaultValues: context.settings.privacy })

  const persist = async (values: PrivacyDto): Promise<boolean> => {
    setError(null)
    try {
      await updatePrivacy(context.settings.id, values)
      reset(values) // the saved values become the new baseline -> no longer dirty
      await context.refetch()
      return true
    } catch (caught) {
      setError(problemMessage(parseProblem(caught), t))
      return false
    }
  }

  const guard = useUnsavedGuard({
    isDirty,
    onSave: () => persist(getValues()),
    onDiscard: () => reset(context.settings.privacy),
  })

  return (
    <SectionPanel title={t('settings.privacy.title')} className="privacy-section">
      <form className="settings-form" onSubmit={handleSubmit(persist)} noValidate>
        {!canEdit && <p className="settings-note">{t('settings.readOnly')}</p>}

        <label className="privacy-toggle">
          <input
            type="checkbox"
            className="privacy-toggle-input"
            disabled={!canEdit}
            {...register('showJobPostings')}
          />
          <span className="privacy-toggle-box" aria-hidden>
            <IconCheck />
          </span>
          <span className="privacy-toggle-text">
            <span className="privacy-toggle-label">{t('settings.privacy.showJobPostings')}</span>
            <span className="privacy-toggle-hint">{t('settings.privacy.showJobPostingsHint')}</span>
          </span>
        </label>

        {canEdit && (
          <SaveBar error={error}>
            <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={!isDirty || isSubmitting}>
              {isSubmitting ? t('common.saving') : t('common.saveChanges')}
            </Button>
          </SaveBar>
        )}
      </form>

      <UnsavedChangesModal guard={guard} />
    </SectionPanel>
  )
}

export default PrivacySection
