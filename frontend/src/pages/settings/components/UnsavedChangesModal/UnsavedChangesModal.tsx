import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'
import { SettingsModal } from '../SettingsModal'
import type { UnsavedGuard } from '../../lib/useUnsavedGuard'

interface UnsavedChangesModalProps {
  guard: UnsavedGuard
}

/** Esc and the backdrop mean "stay": they leave the edits intact and send nothing. */
const UnsavedChangesModal =({ guard }: UnsavedChangesModalProps) => {
  const { t } = useTranslation()

  return (
    <SettingsModal
      isOpen={guard.isOpen}
      onClose={guard.stay}
      title={t('settings.unsaved.title')}
      footer={
        <>
          <Button variant={ButtonVariant.GHOST} onClick={guard.discard} disabled={guard.isSaving}>
            {t('settings.unsaved.discard')}
          </Button>
          <Button variant={ButtonVariant.PRIMARY} onClick={guard.save} disabled={guard.isSaving}>
            {guard.isSaving ? t('common.saving') : t('settings.save')}
          </Button>
        </>
      }
    >
      <p>{t('settings.unsaved.body')}</p>
    </SettingsModal>
  )
}

export default UnsavedChangesModal
