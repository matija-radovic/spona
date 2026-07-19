import { IconHome2, IconBriefcase2, IconBuildingFactory2, IconUsers } from '@tabler/icons-react'
import type { NavItem } from './MobileNav'

// `end: true` = active only on the exact path. Companies/Korisnici are search
// landing pages, so a profile underneath (/companies/:slug, /users/:username)
// must not light the tab. Jobs is master-detail - the list stays on screen at
// /jobs/:slug - so it deliberately stays active across its subtree.
export const GUEST_NAV_ITEMS: readonly NavItem[] = [
  { to: '/', labelKey: 'nav.home', icon: IconHome2, end: true },
  { to: '/jobs', labelKey: 'nav.jobs', icon: IconBriefcase2 },
  { to: '/companies', labelKey: 'nav.companies', icon: IconBuildingFactory2, end: true },
]

// Korisnici exists ONLY here - HeaderGuest stays companies-only (user search
// stays obfuscated for guests, §11). Home points at /feed (decision §16.7).
export const AUTH_NAV_ITEMS: readonly NavItem[] = [
  { to: '/feed', labelKey: 'nav.home', icon: IconHome2, end: true },
  { to: '/jobs', labelKey: 'nav.jobs', icon: IconBriefcase2 },
  { to: '/companies', labelKey: 'nav.companies', icon: IconBuildingFactory2, end: true },
  { to: '/users', labelKey: 'nav.users', icon: IconUsers, end: true },
]
