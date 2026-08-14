import type { Icon } from '@tabler/icons-react'
import {
  IconUser,
  IconLock,
  IconBuildingFactory2,
  IconUserCog,
  IconUsers,
  IconSettings,
} from '@tabler/icons-react'

export interface SettingsSection {
  path: string
  labelKey: string
  icon: Icon
}

export const USER_SECTIONS: readonly SettingsSection[] = [
  { path: 'profile', labelKey: 'settings.nav.profile', icon: IconUser },
  { path: 'privacy', labelKey: 'settings.nav.privacy', icon: IconLock },
  { path: 'organizations', labelKey: 'settings.nav.organizations', icon: IconBuildingFactory2 },
  { path: 'account', labelKey: 'settings.nav.account', icon: IconUserCog },
]

export const ORG_SECTIONS: readonly SettingsSection[] = [
  { path: 'profile', labelKey: 'settings.nav.profile', icon: IconBuildingFactory2 },
  { path: 'privacy', labelKey: 'settings.nav.privacy', icon: IconLock },
  { path: 'members', labelKey: 'settings.nav.members', icon: IconUsers },
  { path: 'management', labelKey: 'settings.nav.management', icon: IconSettings },
]

export const OWNER_ONLY_SECTION = 'management'
