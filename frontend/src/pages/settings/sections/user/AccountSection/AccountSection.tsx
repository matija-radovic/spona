import './AccountSection.scss'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { IconKey, IconMail, IconUser } from '@tabler/icons-react'
import { SettingsSection } from '../../../components/SettingsSection'
import { useUserSettingsContext } from '../../../lib/settingsOutletContext'
import { CredentialRow } from './CredentialRow'
import { UsernameForm } from './UsernameForm'
import { EmailForm } from './EmailForm'
import { PasswordForm } from './PasswordForm'
import { DeleteAccountCard } from './DeleteAccountCard'

type CredentialKey = 'username' | 'email' | 'password'

const AccountSection = () => {
  const { t } = useTranslation()
  const { settings, refetch } = useUserSettingsContext()

  const [openRow, setOpenRow] = useState<CredentialKey | null>(null)
  const toggle = (key: CredentialKey) =>
    setOpenRow((current) => (current === key ? null : key))

  return (
    <SettingsSection title={t('settings.account.title')} className="account-section">
      <div className="account-credentials">
        <CredentialRow
          icon={<IconUser />}
          title={t('settings.account.username.title')}
          action={t('settings.account.username.action')}
          actionShort={t('common.change')}
          isOpen={openRow === 'username'}
          onToggle={() => toggle('username')}
        >
          <UsernameForm settings={settings} refetch={refetch} />
        </CredentialRow>

        <CredentialRow
          icon={<IconMail />}
          title={t('settings.account.email.title')}
          action={t('settings.account.email.action')}
          actionShort={t('common.change')}
          hint={t('settings.account.email.helper')}
          isOpen={openRow === 'email'}
          onToggle={() => toggle('email')}
        >
          <EmailForm settings={settings} />
        </CredentialRow>

        <CredentialRow
          icon={<IconKey />}
          title={t('settings.account.password.title')}
          action={t('settings.account.password.action')}
          actionShort={t('common.change')}
          isOpen={openRow === 'password'}
          onToggle={() => toggle('password')}
        >
          <PasswordForm email={settings.email} />
        </CredentialRow>
      </div>

      <DeleteAccountCard />
    </SettingsSection>
  )
}

export default AccountSection
