import { useOutletContext } from 'react-router-dom'
import type { UserSettingsDto } from '@utils/api/settings'
import type { OrgSettingsDto } from '@utils/api/organizations'

/**
 * Discriminated because `PrivacySection` serves both actor kinds from one
 * component: it reads the union, every other section asserts its kind.
 */
export interface UserSettingsOutletContext {
  kind: 'user'
  settings: UserSettingsDto
  /** Re-runs the bootstrap so the guard's copy stays in step after a save. */
  refetch: () => Promise<void>
}

export interface OrgSettingsOutletContext {
  kind: 'org'
  settings: OrgSettingsDto
  refetch: () => Promise<void>
}

export type SettingsOutletContext = UserSettingsOutletContext | OrgSettingsOutletContext

export const useSettingsContext = () => useOutletContext<SettingsOutletContext>()

export const useUserSettingsContext = (): UserSettingsOutletContext => {
  const context = useSettingsContext()
  if (context.kind !== 'user') {
    throw new Error('useUserSettingsContext must be used inside the user settings branch.')
  }
  return context
}

export const useOrgSettingsContext = (): OrgSettingsOutletContext => {
  const context = useSettingsContext()
  if (context.kind !== 'org') {
    throw new Error('useOrgSettingsContext must be used inside the organization settings branch.')
  }
  return context
}
