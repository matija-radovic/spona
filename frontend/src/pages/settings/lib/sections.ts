import {
  IconUser,
  IconLock,
  IconBuildingFactory2,
  IconUserCog,
  IconUsers,
  IconSettings,
} from '@tabler/icons-react'
import type { SectionItem } from '@shared/components'

export const USER_SECTIONS: readonly SectionItem[] = [
  { path: 'profile', labelKey: 'settings.nav.profile', icon: IconUser },
  { path: 'privacy', labelKey: 'settings.nav.privacy', icon: IconLock },
  { path: 'organizations', labelKey: 'settings.nav.organizations', icon: IconBuildingFactory2 },
  { path: 'account', labelKey: 'settings.nav.account', icon: IconUserCog },
]

export const ORG_SECTIONS: readonly SectionItem[] = [
  { path: 'profile', labelKey: 'settings.nav.profile', icon: IconBuildingFactory2 },
  { path: 'privacy', labelKey: 'settings.nav.privacy', icon: IconLock },
  { path: 'members', labelKey: 'settings.nav.members', icon: IconUsers },
  { path: 'management', labelKey: 'settings.nav.management', icon: IconSettings },
]

export const OWNER_ONLY_SECTION = 'management'
