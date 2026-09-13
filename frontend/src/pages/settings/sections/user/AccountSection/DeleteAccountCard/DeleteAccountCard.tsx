import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant, FieldShell, Input } from '@shared/components'
import { useUser } from '@shared/contexts'
import { parseProblem } from '@utils/api/types'
import { deleteAccount } from '@utils/api/settings'
import { SettingsModal } from '../../../../components/SettingsModal'

interface DeleteAccountCardProps {
  username: string
}

const DeleteAccountCard = ({ username }: DeleteAccountCardProps) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { forgetSession } = useUser()

  const [isOpen, setIsOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const close = () => {
    if (busy) return
    setIsOpen(false)
    setPassword('')
    setError(null)
  }

  const confirm = async () => {
    setBusy(true)
    setError(null)
    try {
      await deleteAccount(username, password)
      forgetSession()
      navigate('/')
    } catch (caught) {
      setError(parseProblem(caught).detail || t('errors.serverError'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="settings-card settings-card-danger">
      <h5 className="settings-card-title">{t('settings.account.delete.title')}</h5>
      <p className="settings-card-hint">{t('settings.account.delete.warning')}</p>

      <div className="settings-card-actions">
        <Button variant={ButtonVariant.DANGER} onClick={() => setIsOpen(true)}>
          {t('settings.account.delete.button')}
        </Button>
      </div>

      <SettingsModal
        isOpen={isOpen}
        onClose={close}
        title={t('settings.confirm.question')}
        footer={
          <>
            <Button variant={ButtonVariant.GHOST} onClick={close} disabled={busy}>
              {t('common.cancel')}
            </Button>
            <Button
              variant={ButtonVariant.DANGER}
              onClick={confirm}
              disabled={busy || password === ''}
            >
              {busy ? t('common.loading') : t('settings.account.delete.button')}
            </Button>
          </>
        }
      >
        <p className="settings-modal-warning">{t('settings.account.delete.warning')}</p>

        <FieldShell label={t('settings.account.delete.passwordLabel')}>
          <Input
            type="password"
            autoComplete="current-password"
            value={password}
            error={error ?? undefined}
            onChange={(event) => setPassword(event.target.value)}
          />
        </FieldShell>
      </SettingsModal>
    </section>
  )
}

export default DeleteAccountCard
