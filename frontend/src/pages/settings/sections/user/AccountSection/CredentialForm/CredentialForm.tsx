import type { FormEventHandler, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonVariant } from '@shared/components'

interface CredentialFormProps {
  onSubmit: FormEventHandler<HTMLFormElement>
  submitLabel: string
  busy?: boolean
  /** A further reason to keep submit disabled, on top of `busy`. */
  disabled?: boolean
  error?: string
  success?: string
  /** Secondary action, sitting next to the submit button. */
  aside?: ReactNode
  children: ReactNode
}

const CredentialForm = ({
  onSubmit,
  submitLabel,
  busy,
  disabled,
  error,
  success,
  aside,
  children,
}: CredentialFormProps) => {
  const { t } = useTranslation()
  const status = error || success

  return (
    <form className="credential-form" onSubmit={onSubmit} noValidate>
      {children}

      <div className="credential-form-actions">
        {status && (
          <p
            className={`credential-form-status ${error ? 'credential-form-error' : 'settings-flash'}`}
          >
            {status}
          </p>
        )}

        {aside && <div className="credential-form-aside">{aside}</div>}

        <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={busy || disabled}>
          {busy ? t('common.saving') : submitLabel}
        </Button>
      </div>
    </form>
  )
}

export default CredentialForm
