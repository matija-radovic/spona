import { useState, type SubmitEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Button,
  ButtonVariant,
  Dropdown,
  FieldShell,
  Input,
  type DropdownOption,
} from '@shared/components'
import type { OrganizationRole } from '@utils/api/organizations'
import { assignableRoles } from '../../../../lib/permissions'

interface InviteFormProps {
  viewerRole: OrganizationRole
  /** Resolves to the failure message, or null when the invitation was created. */
  onInvite: (identifier: string, role: OrganizationRole) => Promise<string | null>
}

/** One identifier field: the backend classifies it as a username or an email. */
const InviteForm =({ viewerRole, onInvite }: InviteFormProps) => {
  const { t } = useTranslation()

  const options: DropdownOption[] = assignableRoles(viewerRole).map((role) => ({
    value: role,
    label: t(`settings.roles.${role}`),
  }))

  const [identifier, setIdentifier] = useState('')
  const [role, setRole] = useState<OrganizationRole>(
    options[options.length - 1].value as OrganizationRole,
  )
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: SubmitEvent) => {
    event.preventDefault()
    if (identifier.trim() === '') {
      setError(t('errors.required'))
      return
    }

    setBusy(true)
    setError(null)
    const message = await onInvite(identifier.trim(), role)
    setError(message)
    if (!message) setIdentifier('')
    setBusy(false)
  }

  return (
    <form className="invite-form" onSubmit={submit} noValidate>
      <FieldShell className="invite-form-field" label={t('settings.invitations.identifier')}>
        <Input
          error={error ?? undefined}
          value={identifier}
          onChange={(event) => setIdentifier(event.target.value)}
        />
      </FieldShell>

      <div className="invite-form-controls">
        <Dropdown
          className="invite-form-role"
          label={t('settings.invitations.roleLabel')}
          options={options}
          value={role}
          onChange={(next) => {
            if (typeof next === 'string') setRole(next as OrganizationRole)
          }}
        />

        <Button type="submit" variant={ButtonVariant.PRIMARY} disabled={busy}>
          {busy ? t('common.loading') : t('settings.invitations.invite')}
        </Button>
      </div>
    </form>
  )
}

export default InviteForm
