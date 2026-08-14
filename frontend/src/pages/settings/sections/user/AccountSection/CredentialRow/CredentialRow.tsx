import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, ButtonSize, ButtonVariant } from '@shared/components'

interface CredentialRowProps {
  icon: ReactNode
  title: string
  /** Toggle label while collapsed; open, it reads "Sakrij". */
  action: string
  /** Collapsed label on narrow phones, where `action` no longer fits. */
  actionShort: string
  /** Guidance about the row as a whole - per-field guidance goes on its `helper`. */
  hint?: ReactNode
  isOpen: boolean
  onToggle: () => void
  children: ReactNode
}

const CredentialRow = ({
  icon,
  title,
  action,
  actionShort,
  hint,
  isOpen,
  onToggle,
  children,
}: CredentialRowProps) => {
  const { t } = useTranslation()
  const panelId = useId()

  return (
    <div className={`credential-row${isOpen ? ' is-open' : ''}`}>
      <div className="credential-row-head">
        <span className="credential-row-icon" aria-hidden="true">
          {icon}
        </span>
        <h5 className="credential-row-title">{title}</h5>

        <Button
          variant={isOpen ? ButtonVariant.QUATERNARY : ButtonVariant.SECONDARY}
          size={ButtonSize.SMALL}
          onClick={onToggle}
          aria-expanded={isOpen}
          aria-controls={panelId}
        >
          {isOpen ? (
            t('common.hide')
          ) : (
            <>
              <span className="credential-row-action-full">{action}</span>
              <span className="credential-row-action-short">{actionShort}</span>
            </>
          )}
        </Button>
      </div>

      {isOpen && (
        <div className="credential-row-panel" id={panelId}>
          {hint && <p className="credential-row-hint">{hint}</p>}
          {children}
        </div>
      )}
    </div>
  )
}

export default CredentialRow
