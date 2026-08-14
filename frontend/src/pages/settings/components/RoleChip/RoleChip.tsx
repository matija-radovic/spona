import './RoleChip.scss'
import { useTranslation } from 'react-i18next'
import type { OrganizationRole } from '@utils/api/organizations'
import { RoleChipSize } from './roleChipSize'

interface RoleChipProps {
  role: OrganizationRole
  /** MD matches the Dropdown trigger box, for chips that share a column with one. */
  size?: RoleChipSize
}

const RoleChip =({ role, size = RoleChipSize.SM }: RoleChipProps) => {
  const { t } = useTranslation()

  return (
    <span className={`role-chip role-chip-size-${size} role-chip-${role.toLowerCase()}`}>
      {t(`settings.roles.${role}`)}
    </span>
  )
}

export default RoleChip
